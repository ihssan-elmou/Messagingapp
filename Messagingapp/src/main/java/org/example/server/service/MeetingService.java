package org.example.server.service;

import org.example.model.Group;
import org.example.model.MeetingSession;
import org.example.protocol.Message;
import org.example.protocol.MessageType;
import org.example.server.core.ClientHandler;
import org.example.server.core.GroupManager;
import org.example.server.database.dao.CallDAO;
import org.example.server.database.entity.DbCall;
import org.example.server.network.MediaRelay;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère le cycle de vie des réunions audio/vidéo multi-utilisateurs.
 *
 * Protocole :
 *   MEETING_START  → créateur démarre une réunion pour un groupe
 *   MEETING_JOIN   → un membre rejoint la réunion
 *   MEETING_LEAVE  → un participant quitte
 *
 * Le contenu des messages suit le format :
 *   MEETING_START  : content = groupId
 *   MEETING_JOIN   : content = meetingId
 *   MEETING_LEAVE  : content = meetingId
 *
 * Le serveur répond avec des messages INFO dont le contenu est :
 *   "MEETING_CREATED:<meetingId>"
 *   "MEETING_INVITE:<meetingId>:<groupName>"
 *   "MEETING_JOINED:<meetingId>:<username>"
 *   "MEETING_LEFT:<meetingId>:<username>"
 *   "MEETING_ENDED:<meetingId>"
 *   "MEETING_ERROR:<raison>"
 */
public class MeetingService {

    // meetingId -> session active
    private final Map<String, MeetingSession> activeMeetings = new ConcurrentHashMap<>();

    private final Map<String, ClientHandler> clients;
    private final GroupManager               groupManager;
    private final MediaRelay                 audioRelay;
    private final MediaRelay                 videoRelay;
    private final CallDAO                    callDAO = new CallDAO();

    public MeetingService(Map<String, ClientHandler> clients,
                          GroupManager groupManager,
                          MediaRelay audioRelay,
                          MediaRelay videoRelay) {
        this.clients      = clients;
        this.groupManager = groupManager;
        this.audioRelay   = audioRelay;
        this.videoRelay   = videoRelay;
    }

    // ------------------------------------------------------------------ //
    //  Point d'entrée principal
    // ------------------------------------------------------------------ //

    public void handle(Message msg) {
        if (msg == null || msg.getType() == null) return;
        switch (msg.getType()) {
            case MEETING_START: startMeeting(msg); break;
            case MEETING_JOIN:  joinMeeting(msg);  break;
            case MEETING_LEAVE: leaveMeeting(msg); break;
            default: break;
        }
    }

    // ------------------------------------------------------------------ //
    //  MEETING_START
    // ------------------------------------------------------------------ //

    private void startMeeting(Message msg) {
        String initiator = msg.getSender();
        // format content: "groupId" ou "groupId|VIDEO"
        String raw = msg.getContent();
        String groupId, meetingType;
        if (raw != null && raw.contains("|")) {
            String[] parts = raw.split("\\|", 2);
            groupId     = parts[0];
            meetingType = parts[1]; // "VIDEO" ou "AUDIO"
        } else {
            groupId     = raw;
            meetingType = "AUDIO";
        }

        if (groupId == null || groupId.isBlank()) {
            sendError(initiator, "GroupId manquant");
            return;
        }

        Group group = groupManager.getGroup(groupId);
        if (group == null) {
            sendError(initiator, "Groupe introuvable : " + groupId);
            return;
        }

        // Vérifier que l'initiateur appartient au groupe
        if (!group.hasMember(initiator)) {
            sendError(initiator, "Vous n'êtes pas membre de ce groupe");
            return;
        }

        // Créer la session
        String         meetingId = UUID.randomUUID().toString();
        MeetingSession session   = new MeetingSession(meetingId, groupId, meetingType, initiator);
        session.addParticipant(initiator);
        activeMeetings.put(meetingId, session);

        System.out.println("[MeetingService] Réunion créée : " + meetingId
                + " par " + initiator + " pour groupe " + group.getName());

        // Confirmer à l'initiateur (+ liste initiale = lui seul)
        sendInfo(initiator, "MEETING_CREATED:" + meetingId + ":" + meetingType);
        sendInfo(initiator, "MEETING_ROSTER:" + meetingId + "|||" + initiator);

        // Inviter tous les autres membres du groupe
        for (String member : group.getMembers()) {
            if (!member.equals(initiator)) {
                // Séparateur ||| pour éviter les conflits si le nom du groupe contient ":"
                sendInfo(member, "MEETING_INVITE:" + meetingId + "|||" + group.getName()
                        + "|||" + initiator + "|||" + meetingType);
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  MEETING_JOIN
    // ------------------------------------------------------------------ //

    private void joinMeeting(Message msg) {
        String joiner    = msg.getSender();
        String meetingId = msg.getContent();

        if (meetingId == null || meetingId.isBlank()) {
            sendError(joiner, "MeetingId manquant");
            return;
        }

        MeetingSession session = activeMeetings.get(meetingId);
        if (session == null || !session.isActive()) {
            sendError(joiner, "Réunion introuvable ou terminée : " + meetingId);
            return;
        }

        // Vérifier appartenance au groupe (avec fallback BDD)
        Group group = groupManager.getGroup(session.getGroupId());
        if (group == null) {
            // Recharger depuis BDD
            org.example.server.database.dao.GroupDAO groupDAO = new org.example.server.database.dao.GroupDAO();
            org.example.server.database.dao.GroupDAO.GroupRow row = groupDAO.findById(session.getGroupId());
            if (row != null) {
                group = groupManager.createGroupFromDB(row.id, row.name, row.admin);
                for (String m : groupDAO.getMembers(row.id)) group.addMember(m);
            }
        }
        if (group == null || !group.hasMember(joiner)) {
            // Tolérer si le groupe n'est pas trouvé mais que la session existe
            if (group == null) {
                System.out.println("[MeetingService] Groupe non trouvé en mémoire, accès toléré pour " + joiner);
            } else {
                sendError(joiner, "Accès refusé à la réunion");
                return;
            }
        }

        // Eviter les doublons
        if (!session.getParticipants().contains(joiner)) {
            session.addParticipant(joiner);
        }

        System.out.println("[MeetingService] " + joiner + " a rejoint la réunion " + meetingId
                + " — participants : " + session.getParticipants());

        // Confirmer au joiner + envoyer la liste complète des participants (sync grille UI)
        sendInfo(joiner, "MEETING_JOINED_CONFIRM:" + meetingId);
        broadcastMeetingRoster(session, meetingId);

        // Notifier aussi chaque arrivée individuelle (audio / logs)
        for (String existing : session.getParticipants()) {
            if (!existing.equals(joiner)) {
                sendInfo(joiner, "MEETING_JOINED:" + meetingId + "|||" + existing);
            }
        }
        broadcastToMeeting(session, "MEETING_JOINED:" + meetingId + "|||" + joiner, joiner);
    }

    /** Envoie à tous les participants la liste complète (évite « je ne vois que moi »). */
    private void broadcastMeetingRoster(MeetingSession session, String meetingId) {
        String roster = String.join(",", session.getParticipants());
        String payload = "MEETING_ROSTER:" + meetingId + "|||" + roster;
        for (String participant : session.getParticipants()) {
            sendInfo(participant, payload);
        }
    }

    // ------------------------------------------------------------------ //
    //  MEETING_LEAVE
    // ------------------------------------------------------------------ //

    private void leaveMeeting(Message msg) {
        String leaver    = msg.getSender();
        String meetingId = msg.getContent() != null ? msg.getContent().trim() : null;

        if (meetingId == null || meetingId.isBlank()) return;

        MeetingSession session = activeMeetings.get(meetingId);
        if (session == null) return;

        session.removeParticipant(leaver);

        // Libérer les ports UDP de ce participant
        audioRelay.unregisterPeer(leaver);
        videoRelay.unregisterPeer(leaver);

        System.out.println("[MeetingService] " + leaver + " a quitté la réunion " + meetingId
                + " — participants restants : " + session.getParticipants());

        if (session.getParticipants().isEmpty()) {
            recordGroupCall(session);
            session.setActive(false);
            activeMeetings.remove(meetingId);
            System.out.println("[MeetingService] Réunion terminée (plus de participants) : " + meetingId);
        } else {
            // Notifier les restants
            broadcastToMeeting(session, "MEETING_LEFT:" + meetingId + "|||" + leaver, null);
        }
    }

    // ------------------------------------------------------------------ //
    //  Helpers
    // ------------------------------------------------------------------ //

    /**
     * Diffuse un message INFO à tous les participants d'une réunion.
     * @param exclude si non null, ce participant est exclu de la diffusion
     */
    private void broadcastToMeeting(MeetingSession session, String infoContent, String exclude) {
        for (String participant : session.getParticipants()) {
            if (participant.equals(exclude)) continue;
            sendInfo(participant, infoContent);
        }
    }

    private void sendInfo(String username, String content) {
        ClientHandler ch = clients.get(username);
        if (ch != null) {
            ch.sendObject(new Message(MessageType.INFO, "SERVER", username, content));
        }
    }

    private void sendError(String username, String reason) {
        ClientHandler ch = clients.get(username);
        if (ch != null) {
            ch.sendObject(new Message(MessageType.ERROR, "SERVER", username,
                    "MEETING_ERROR:" + reason));
        }
    }

    // ------------------------------------------------------------------ //
    //  Accès public
    // ------------------------------------------------------------------ //

    public MeetingSession getMeeting(String meetingId) {
        return activeMeetings.get(meetingId);
    }

    /** Retourne tous les participants actifs d'une réunion, ou null. */
    public java.util.List<String> getParticipants(String meetingId) {
        MeetingSession s = activeMeetings.get(meetingId);
        return s == null ? null : s.getParticipants();
    }

    /** Retire un utilisateur de toutes ses réunions actives (déconnexion brutale). */
    public void leaveAllMeetingsFor(String username) {
        if (username == null || username.isBlank()) return;
        for (MeetingSession session : activeMeetings.values()) {
            if (!session.isActive() || !session.getParticipants().contains(username)) continue;
            String meetingId = session.getId();
            session.removeParticipant(username);
            audioRelay.unregisterPeer(username);
            videoRelay.unregisterPeer(username);
            if (session.getParticipants().isEmpty()) {
                recordGroupCall(session);
                session.setActive(false);
                activeMeetings.remove(meetingId);
            } else {
                broadcastToMeeting(session, "MEETING_LEFT:" + meetingId + "|||" + username, null);
            }
        }
    }

    private void recordGroupCall(MeetingSession session) {
        if (session == null || session.getGroupId() == null) return;
        String caller = session.getStartedBy() != null ? session.getStartedBy() : "SYSTEM";
        String type   = "VIDEO".equalsIgnoreCase(session.getMeetingType()) ? "VIDEO" : "AUDIO";
        DbCall call = new DbCall(caller, null, session.getGroupId(), type, "ENDED");
        callDAO.create(call);
        System.out.println("[MeetingService] Appel de groupe enregistré : " + session.getGroupId());
    }
}
