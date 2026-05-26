package org.example.server.service;

import org.example.model.Group;
import org.example.protocol.*;
import org.example.server.core.ClientHandler;
import org.example.server.core.GroupManager;
import org.example.server.database.dao.FileDAO;
import org.example.server.database.dao.GroupDAO;
import org.example.server.database.dao.MessageDAO;
import org.example.server.database.dao.UserDAO;
import org.example.server.database.entity.DbFile;
import org.example.server.database.entity.DbMessage;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class GroupService {

    private final Map<String, ClientHandler> clients;
    private final GroupManager               groupManager;
    private final FileDAO                    fileDAO    = new FileDAO();
    private final GroupDAO                   groupDAO   = new GroupDAO();
    private final MessageDAO                 messageDAO = new MessageDAO();
    private final UserDAO                    userDAO    = new UserDAO();

    public GroupService(Map<String, ClientHandler> clients, GroupManager groupManager) {
        this.clients      = clients;
        this.groupManager = groupManager;
        System.out.println("[GroupService] Initialisé.");
    }

    public void handle(Message msg) {
        if (msg == null || msg.getType() == null) return;
        switch (msg.getType()) {
            case GROUP_CREATE:        createGroup(msg);       break;
            case GROUP_ADD_MEMBER:    addMember(msg);         break;
            case GROUP_REMOVE_MEMBER: removeMember(msg);      break;
            case GROUP_MESSAGE:       broadcastToGroup(msg);  break;
            case GROUP_LIST:          sendGroupList(msg);     break;
            case LOAD_GROUP_HISTORY:  sendGroupHistory(msg);  break;
            default: break;
        }
    }

    public void handleGroupFile(GroupFileMessage gfm) {
        if (gfm == null) return;
        Group g = groupManager.getGroup(gfm.getGroupId());
        if (g == null) return;
        // Sauvegarder le nom dans messages (ordre chronologique)
        messageDAO.create(new DbMessage(gfm.getSender(), null, gfm.getGroupId(),
                gfm.getFileName(), "FILE", null));
        // Sauvegarder les données binaires dans files (pour l'historique après redémarrage)
        fileDAO.create(new DbFile(gfm.getSender(), null, gfm.getGroupId(),
                gfm.getFileName(), gfm.getFileType(),
                gfm.getData() != null ? gfm.getData().length : 0,
                null, gfm.getData()));
        for (String member : g.getMembers()) {
            if (member.equals(gfm.getSender())) continue;
            ClientHandler ch = clients.get(member);
            if (ch != null) ch.sendObject(gfm);
        }
    }

    // ------------------------------------------------------------------ //
    //  Création
    // ------------------------------------------------------------------ //

    private void createGroup(Message msg) {
        String raw = msg.getContent();
        String groupName;
        List<String> extraMembers = new ArrayList<>();

        if (raw.contains("|")) {
            String[] split = raw.split("\\|", 2);
            groupName = split[0].trim();
            if (split.length > 1 && !split[1].isBlank()) {
                for (String m : split[1].split(",")) {
                    String u = m.trim();
                    if (!u.isEmpty() && !u.equals(msg.getSender())) extraMembers.add(u);
                }
            }
        } else {
            groupName = raw.trim();
        }

        // Group() constructeur ajoute l'admin en mémoire automatiquement
        Group g = groupManager.createGroup(groupName, msg.getSender());

        // Persister en BDD : groupe + admin (le seul addMember pour l'admin)
        userDAO.ensureUser(msg.getSender());
        groupDAO.createGroup(g.getId(), groupName, msg.getSender());
        groupDAO.addMember(g.getId(), msg.getSender(), "ADMIN"); // ← unique ajout admin

        // Ajouter membres supplémentaires
        for (String member : extraMembers) {
            g.addMember(member);       // en mémoire
            userDAO.ensureUser(member);
            groupDAO.addMember(g.getId(), member, "MEMBER"); // en BDD
        }

        // Membres finaux (admin + extras)
        String membersStr = String.join(",", g.getMembers());

        // Confirmer au créateur
        ClientHandler creator = clients.get(msg.getSender());
        if (creator != null)
            creator.sendObject(new Message(MessageType.INFO, "SERVER", msg.getSender(),
                    "GROUP_CREATED:" + g.getId() + ":" + groupName + ":" + membersStr));

        // Notifier les membres ajoutés
        for (String member : extraMembers) {
            ClientHandler ch = clients.get(member);
            if (ch != null)
                ch.sendObject(new Message(MessageType.INFO, "SERVER", member,
                        "GROUP_ADDED:" + g.getId() + ":" + groupName + ":" + msg.getSender()
                                + ":" + membersStr));
        }

        System.out.println("[GroupService] Groupe créé : " + groupName
                + " (" + g.getId() + ") membres=" + g.getMembers());
    }

    // ------------------------------------------------------------------ //
    //  Ajout membre
    // ------------------------------------------------------------------ //

    private void addMember(Message msg) {
        // content = "groupId:username"
        String[] parts = msg.getContent().split(":", 2);
        if (parts.length < 2) return;
        Group g = groupManager.getGroup(parts[0]);
        if (g == null) return;
        String newMember = parts[1].trim();
        if (newMember.isEmpty()) return;

        g.addMember(newMember);
        userDAO.ensureUser(newMember);
        groupDAO.addMember(g.getId(), newMember, "MEMBER");

        // Notifier le nouveau membre avec la liste complète des membres
        String membersStr = String.join(",", g.getMembers());
        ClientHandler ch = clients.get(newMember);
        if (ch != null)
            ch.sendObject(new Message(MessageType.INFO, "SERVER", newMember,
                    "GROUP_ADDED:" + g.getId() + ":" + g.getName() + ":" + g.getAdmin()
                            + ":" + membersStr));

        // Notifier tous les membres actuels
        for (String member : g.getMembers()) {
            if (!member.equals(newMember)) {
                ClientHandler mch = clients.get(member);
                if (mch != null)
                    mch.sendObject(new Message(MessageType.INFO, "SERVER", member,
                            "GROUP_MEMBER_JOINED:" + g.getId() + ":" + newMember
                                    + ":" + membersStr));
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  Retrait membre
    // ------------------------------------------------------------------ //

    private void removeMember(Message msg) {
        String[] parts = msg.getContent().split(":", 2);
        if (parts.length < 2) return;
        Group g = groupManager.getGroup(parts[0]);
        if (g == null) return;
        String removed = parts[1];
        g.removeMember(removed);
        groupDAO.removeMember(g.getId(), removed);

        ClientHandler ch = clients.get(removed);
        if (ch != null)
            ch.sendObject(new Message(MessageType.INFO, "SERVER", removed,
                    "GROUP_REMOVED:" + g.getId() + ":" + g.getName()));

        // Message système: notifier les membres restants + envoyer notif de retrait
        String sysMsg = "🚫 " + removed + " a été retiré du groupe.";
        messageDAO.create(new DbMessage("SYSTEM", null, g.getId(), sysMsg, "SYSTEM", null));

        for (String member : g.getMembers()) {
            ClientHandler mch = clients.get(member);
            if (mch != null) {
                mch.sendObject(new Message(MessageType.INFO, "SERVER", member,
                        "GROUP_MEMBER_LEFT:" + g.getId() + ":" + removed));
                // Envoyer le message système à chaque membre restant
                Message sysMsgObj = new Message(MessageType.GROUP_MESSAGE, "SYSTEM", null, sysMsg);
                sysMsgObj.setGroupId(g.getId());
                mch.sendObject(sysMsgObj);
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  Broadcast message
    //  FIX: le serveur broadcast à tous SAUF l'expéditeur (qui affiche lui-même)
    // ------------------------------------------------------------------ //

    public void deleteGroupMessage(Message msg) {
        if (msg == null || msg.getContent() == null) return;
        String[] p = msg.getContent().split("\\|", 3);
        if (p.length < 3) return;
        int messageId;
        try { messageId = Integer.parseInt(p[1]); } catch (NumberFormatException e) { return; }
        String groupId = p[2];

        Group g = groupManager.getGroup(groupId);
        if (g == null) return;

        DbMessage db = messageDAO.findById(messageId);
        if (db == null || db.getGroupId() == null || !db.getGroupId().equals(groupId)) return;

        boolean isOwner = msg.getSender().equals(db.getSender());
        boolean isAdmin = msg.getSender().equals(g.getAdmin());
        if (!isOwner && !isAdmin) return;

        messageDAO.delete(messageId);
        String payload = "GROUP|" + messageId + "|" + groupId;
        Message notif = new Message(MessageType.DELETE_MESSAGE, msg.getSender(), null, payload);
        notif.setGroupId(groupId);
        for (String member : g.getMembers()) {
            ClientHandler ch = clients.get(member);
            if (ch != null) ch.sendObject(notif);
        }
    }

    private void broadcastToGroup(Message msg) {
        String groupId = msg.getGroupId();
        if (groupId == null) return;
        Group g = groupManager.getGroup(groupId);
        if (g == null) return;

        // FIX: vérifier que l'expéditeur est encore membre (peut avoir été retiré)
        if (!g.hasMember(msg.getSender())) {
            ClientHandler ch = clients.get(msg.getSender());
            if (ch != null) ch.sendObject(new Message(MessageType.ERROR, "SERVER",
                    msg.getSender(), "Vous avez été retiré de ce groupe."));
            return;
        }

        // Persister en BDD
        messageDAO.create(new DbMessage(msg.getSender(), null, groupId,
                msg.getContent(), "TEXT", null));

        // Envoyer à tous les membres SAUF l'expéditeur (qui a déjà affiché le message)
        for (String member : g.getMembers()) {
            if (member.equals(msg.getSender())) continue; // ← FIX doublon
            ClientHandler ch = clients.get(member);
            if (ch != null) ch.sendObject(msg);
        }
    }

    // ------------------------------------------------------------------ //
    //  Historique groupe
    // ------------------------------------------------------------------ //

    private void sendGroupHistory(Message msg) {
        String groupId   = msg.getContent();
        String requester = msg.getSender();
        if (groupId == null || groupId.isBlank()) return;

        // FIX: Nouveaux membres voient seulement les messages depuis leur adhésion
        String joinedAt = groupDAO.getJoinedAt(groupId, requester);
        List<DbMessage> messages = messageDAO.findGroupMessages(groupId, joinedAt);
        StringBuilder sb = new StringBuilder();
        for (DbMessage m : messages) {
            // Encoder le contenu en base64 pour éviter les \t et \n dans le contenu
            String encoded = Base64.getEncoder().encodeToString(
                    (m.getContent() == null ? "" : m.getContent())
                            .getBytes(StandardCharsets.UTF_8));
            // Format: sender \t groupId \t sentAt \t content_b64 \t messageType \n
            sb.append(m.getId()).append('\t')
              .append(nullToEmpty(m.getSender())).append('\t')
              .append(nullToEmpty(m.getGroupId())).append('\t')
              .append(nullToEmpty(normalizeDbTimestamp(m.getSentAt()))).append('\t')
              .append(encoded).append('\t')
              .append(nullToEmpty(m.getMessageType())).append('\n');
        }

        ClientHandler ch = clients.get(requester);
        if (ch != null) {
            // 1. Historique texte
            Message resp = new Message(MessageType.GROUP_HISTORY_RESPONSE,
                    "SERVER", requester, sb.toString());
            resp.setGroupId(groupId);
            ch.sendObject(resp);
            // 2. Fichiers : ordre chronologique = sent_at du message FILE (pas uploaded_at)
            for (DbMessage m : messages) {
                if (!"FILE".equalsIgnoreCase(m.getMessageType())) continue;
                String fn = m.getContent();
                if (fn == null || fn.isBlank()) continue;
                DbFile f = fileDAO.findGroupFile(groupId, m.getSender(), fn);
                if (f == null || f.getData() == null || f.getData().length == 0) continue;
                ch.sendObject(new GroupFileMessage(
                        f.getSender(), groupId,
                        f.getFileName(), f.getFileType(), f.getData(),
                        normalizeDbTimestamp(m.getSentAt())));
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  Liste groupes de l'utilisateur
    // ------------------------------------------------------------------ //

    private void sendGroupList(Message msg) {
        String username = msg.getSender();
        List<GroupDAO.GroupRow> rows = groupDAO.getGroupsOfUser(username);
        StringBuilder sb = new StringBuilder();

        for (GroupDAO.GroupRow row : rows) {
            // Recharger le groupe en mémoire depuis BDD si absent
            Group g = groupManager.getGroup(row.id);
            if (g == null) {
                g = groupManager.createGroupFromDB(row.id, row.name, row.admin);
                // Charger les membres depuis BDD
                for (String m : groupDAO.getMembers(row.id)) g.addMember(m);
            }
            // Membres toujours depuis BDD pour garantir la fraîcheur
            List<String> members = groupDAO.getMembers(row.id);
            String preview = "";
            String lastTime = "";
            DbMessage last = messageDAO.findLastGroupMessage(g.getId());
            if (last != null) {
                lastTime = normalizeDbTimestamp(last.getSentAt());
                String me = username;
                if ("FILE".equalsIgnoreCase(last.getMessageType())) {
                    preview = last.getSender().equals(me) ? "Vous: 📎 " + last.getContent() : "📎 " + last.getContent();
                } else if ("SYSTEM".equalsIgnoreCase(last.getMessageType())) {
                    preview = last.getContent() != null ? last.getContent() : "";
                } else {
                    preview = last.getSender().equals(me) ? "Vous: " + last.getContent() : last.getSender() + ": " + last.getContent();
                }
            }
            String encPreview = Base64.getEncoder().encodeToString(
                    (preview == null ? "" : preview).getBytes(StandardCharsets.UTF_8));
            sb.append(g.getId()).append(":")
              .append(g.getName()).append(":")
              .append(g.getAdmin()).append(":")
              .append(String.join(",", members)).append("|")
              .append(encPreview).append("|")
              .append(nullToEmpty(lastTime))
              .append("\n");
        }

        ClientHandler ch = clients.get(username);
        if (ch != null)
            ch.sendObject(new Message(MessageType.INFO, "SERVER", username,
                    "GROUP_LIST:" + sb));
    }

    private String nullToEmpty(String v) { return v == null ? "" : v; }

    private static String normalizeDbTimestamp(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        String s = raw.trim().replace('T', ' ');
        int dot = s.indexOf('.');
        if (dot > 0) s = s.substring(0, dot);
        if (s.length() > 19) s = s.substring(0, 19);
        return s;
    }
}
