package org.example.client.controller;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import org.example.client.network.*;
import org.example.client.ui.ChatView;
import org.example.model.CallRequest;
import org.example.protocol.*;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ChatController V2 — intègre groupes, fichiers groupe, réunions.
 */
public class ChatController implements ClientListener {

    private String              username;
    private ClientSocketManager socketManager;
    private RequestSender       sender;
    private ChatView            chatView;
    private String              pendingAuthDisplay;

    private CallController      callController;
    private GroupController     groupController;
    private MeetingController   meetingController;

    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);

    public ChatController(String username) { this.username = username; }

    public void setCanonicalUsername(String canonical) {
        if (canonical != null && !canonical.isBlank()) this.username = canonical;
        if (socketManager != null) socketManager.setUsername(this.username);
    }

    public void init(ClientSocketManager sm) {
        this.socketManager     = sm;
        this.sender            = new RequestSender(sm);
        this.callController    = new CallController(username, sender, sm.getHost());
        this.groupController   = new GroupController(username, sender);
        this.meetingController = new MeetingController(username, sender, sm.getHost());
    }

    public void setChatView(ChatView v) {
        this.chatView = v;
        callController.setChatView(v);
        if (pendingAuthDisplay != null) {
            chatView.onAuthOk(pendingAuthDisplay);
        }
        loadContacts();
        groupController.requestGroupList();
    }

    // ------------------------------------------------------------------ //
    //  Actions UI privé
    // ------------------------------------------------------------------ //

    public void sendMessage(String to, String text) {
        sender.sendText(to, text);
        if (chatView != null) chatView.addContact(to);
    }

    public void loadContacts() { if (sender != null) sender.requestContacts(); }

    public void loadHistory(String peer) {
        if (sender != null && peer != null && !peer.isBlank()) {
            sender.requestHistory(peer);
            sender.requestCallHistory(peer);
        }
    }

    public void startAudioCall(String peer) {
        if (peer == null || peer.isBlank()) return;
        callController.initiateCall(peer, CallRequest.CallType.AUDIO);
        if (chatView != null) chatView.addContact(peer);
    }

    public void startVideoCall(String peer) {
        if (peer == null || peer.isBlank()) return;
        callController.initiateCall(peer, CallRequest.CallType.VIDEO);
        if (chatView != null) chatView.addContact(peer);
    }

    public void endCall() { callController.endCall(); }

    public void logout() {
        reconnectScheduled.set(true);
        sender.logout();
    }

    public void sendFile(String to, String fileName, String fileType, byte[] data) {
        sender.sendFile(to, fileName, fileType, data);
        if (chatView != null) chatView.addContact(to);
    }

    // ------------------------------------------------------------------ //
    //  Actions UI groupes
    // ------------------------------------------------------------------ //

    /** Créer un groupe avec une liste de membres. */
    public void createGroup(String groupName, java.util.List<String> members) {
        groupController.createGroupWithMembers(groupName, members);
    }

    /** Créer un groupe sans membres (si aucun contact disponible). */
    public void createGroupNoMembers(String groupName) {
        groupController.createGroup(groupName);
    }

    public void addMember(String groupId, String username) {
        groupController.addMember(groupId, username);
    }

    public void removeMember(String groupId, String username) {
        groupController.removeMember(groupId, username);
    }

    public void deletePrivateMessage(String peer, int messageId) {
        sender.sendMessage(new Message(MessageType.DELETE_MESSAGE, username, peer,
                "PRIVATE|" + messageId + "|" + peer));
    }

    public void deleteGroupMessage(String groupId, int messageId) {
        Message m = new Message(MessageType.DELETE_MESSAGE, username, null,
                "GROUP|" + messageId + "|" + groupId);
        m.setGroupId(groupId);
        sender.sendMessage(m);
    }

    public void sendGroupMessage(String groupId, String text) {
        groupController.sendGroupMessage(groupId, text);
    }

    public void sendGroupFile(String groupId, String fileName, String fileType, byte[] data) {
        groupController.sendGroupFile(groupId, fileName, fileType, data);
    }

    public void loadAllCallHistory() {
        sender.sendMessage(new Message(MessageType.LOAD_CALL_HISTORY_ALL, username, null, ""));
    }

    public void loadGroupHistory(String groupId) {
        sender.sendMessage(new Message(MessageType.LOAD_GROUP_HISTORY, username, null, groupId));
    }

    // ------------------------------------------------------------------ //
    //  Actions UI réunions groupe
    // ------------------------------------------------------------------ //

    public void startGroupAudioMeeting(String groupId, String groupName) {
        meetingController.startMeeting(groupId, groupName);
    }

    public void startGroupVideoMeeting(String groupId, String groupName) {
        meetingController.startVideoMeeting(groupId, groupName);
    }

    // ------------------------------------------------------------------ //
    //  Callbacks réseau
    // ------------------------------------------------------------------ //

    @Override
    public void onFileReceived(FileMessage fm) {
        Platform.runLater(() -> {
            if (chatView != null) {
                boolean fromMe = username.equals(fm.getSender());
                String peer = fromMe ? fm.getReceiver() : fm.getSender();
                if (peer == null || peer.isBlank()) return;
                chatView.addContact(peer);
                // Passer uploadedAt : timestamp réel BDD pour tri chronologique
                chatView.appendFile(peer, fm.getFileName(),
                        fm.getFileType(), fm.getData(), fromMe, fm.getUploadedAt());
            }
        });
    }

    public void onGroupFileReceived(GroupFileMessage gfm) {
        Platform.runLater(() -> {
            if (chatView != null) {
                boolean fromMe = username.equals(gfm.getSender());
                chatView.appendGroupFile(gfm.getGroupId(), gfm.getSender(),
                        gfm.getFileName(), gfm.getFileType(), gfm.getData(), fromMe, gfm.getUploadedAt());
            }
        });
    }

    @Override
    public void onMessage(Message msg) {
        Platform.runLater(() -> {
            switch (msg.getType()) {
                case TEXT:
                    if (chatView != null) {
                        chatView.addContact(msg.getSender());
                        chatView.appendMessage(msg.getSender(), msg.getContent(), false);
                    }
                    break;
                case USER_LIST:
                    if (chatView != null) chatView.updateUserList(msg.getContent());
                    break;
                case CONTACTS_RESPONSE:
                    if (chatView != null) chatView.updateSavedContacts(msg.getContent());
                    break;
                case HISTORY_RESPONSE:
                    if (chatView != null) chatView.setConversationHistory(msg.getReceiver(), msg.getContent());
                    break;
                case CALL_HISTORY_RESPONSE:
                    if (chatView != null) chatView.setCallHistory(msg.getReceiver(), msg.getContent());
                    break;
                case GROUP_MESSAGE:
                    if (chatView != null) {
                        boolean fromMe = username.equals(msg.getSender());
                        chatView.appendGroupMessage(msg.getGroupId(), msg.getSender(),
                                msg.getContent(), fromMe);
                    }
                    break;
                case GROUP_HISTORY_RESPONSE: {
                    String gid = msg.getGroupId();
                    if (gid == null || gid.isBlank()) {
                        gid = msg.getContent() != null && msg.getContent().contains("\t")
                                ? msg.getContent().split("\\R")[0].split("\t", -1)[1]
                                : null;
                    }
                    if (chatView != null) chatView.setGroupHistory(gid, msg.getContent());
                    break;
                }
                case CALL_HISTORY_ALL_RESPONSE:
                    if (chatView != null) chatView.setAllCallHistory(msg.getContent());
                    break;
                case DELETE_MESSAGE:
                    if (chatView != null) chatView.onMessageDeleted(msg.getContent());
                    break;
                case CALL_ACCEPT:  callController.onCallAccepted(msg);  break;
                case CALL_REJECT:  callController.onCallRejected(msg);  break;
                case CALL_END:     callController.onCallEnded(msg);     break;
                case INFO:         routeInfoMessage(msg);  break;
                case ERROR:        handleError(msg.getContent());        break;
                default: break;
            }
        });
    }

    // ------------------------------------------------------------------ //
    //  Routage INFO
    // ------------------------------------------------------------------ //

    private void routeInfoMessage(Message msg) {
        if (msg == null) return;
        String content = msg.getContent();
        if (content == null) return;

        // Réunions : ne pas dépendre de chatView (sinon les MEETING_JOINED sont perdus)
        if (content.startsWith("MEETING_")) {
            routeMeetingInfo(content);
            return;
        }
        if (content.startsWith("AUTH_OK:")) {
            String display = content.substring("AUTH_OK:".length());
            if (msg.getReceiver() != null && !msg.getReceiver().isBlank()) {
                setCanonicalUsername(msg.getReceiver());
            }
            pendingAuthDisplay = display;
            if (chatView != null) chatView.onAuthOk(display);
            return;
        }
        if (chatView == null) return;

        if (content.startsWith("GROUP_CREATED:")) {
            // "GROUP_CREATED:<id>:<name>:<member1,member2,...>"
            String[] p = content.substring("GROUP_CREATED:".length()).split(":", 3);
            if (p.length >= 3) {
                java.util.List<String> members = java.util.Arrays.asList(p[2].split(","));
                chatView.onGroupCreated(p[0], p[1], members);
            }

        } else if (content.startsWith("GROUP_ADDED:")) {
            // "GROUP_ADDED:<id>:<name>:<admin>:<members,...>"
            String[] p = content.substring("GROUP_ADDED:".length()).split(":", 4);
            if (p.length >= 2) {
                java.util.List<String> mbs = p.length >= 4
                        ? java.util.Arrays.asList(p[3].split(","))
                        : new java.util.ArrayList<>();
                // Filtrer les membres vides
                mbs = mbs.stream().filter(m -> !m.isBlank()).collect(java.util.stream.Collectors.toList());
                chatView.onGroupAdded(p[0], p.length >= 2 ? p[1] : "", mbs);
            }

        } else if (content.startsWith("GROUP_REMOVED:")) {
            // "GROUP_REMOVED:<id>:<name>" — l'utilisateur a été retiré
            String[] p = content.substring("GROUP_REMOVED:".length()).split(":", 2);
            if (p.length >= 1 && chatView != null) chatView.onGroupRemoved(p[0], p.length >= 2 ? p[1] : "");

        } else if (content.startsWith("GROUP_MEMBER_JOINED:")) {
            // Format: "groupId:username:m1,m2,..."
            String[] p = content.substring("GROUP_MEMBER_JOINED:".length()).split(":", 3);
            if (p.length >= 2) {
                java.util.List<String> allMembers = p.length >= 3
                        ? java.util.Arrays.asList(p[2].split(","))
                        : new java.util.ArrayList<>();
                chatView.onMemberJoined(p[0], p[1], allMembers);
            }
        } else if (content.startsWith("GROUP_MEMBER_LEFT:")) {
            String[] p = content.substring("GROUP_MEMBER_LEFT:".length()).split(":", 2);
            if (p.length == 2) chatView.onMemberLeft(p[0], p[1]);

        } else if (content.startsWith("GROUP_LIST:")) {
            chatView.onGroupList(content.substring("GROUP_LIST:".length()));

        }
    }

    private void routeMeetingInfo(String content) {
        if (content.startsWith("MEETING_CREATED:")) {
            String rest = content.substring("MEETING_CREATED:".length());
            meetingController.onMeetingCreated(rest, "");

        } else if (content.startsWith("MEETING_INVITE:")) {
            String rest = content.substring("MEETING_INVITE:".length());
            String[] p = rest.split("\\|\\|\\|", 4);
            if (p.length >= 3) {
                String type = p.length >= 4 ? p[3] : "AUDIO";
                meetingController.onMeetingInvite(p[0], p[1], p[2], type);
            }

        } else if (content.startsWith("MEETING_JOINED_CONFIRM:")) {
            String meetingId = content.substring("MEETING_JOINED_CONFIRM:".length()).trim();
            System.out.println("[Meeting] Rejoint avec succès : " + meetingId);
            meetingController.onMeetingJoinConfirmed(meetingId);

        } else if (content.startsWith("MEETING_JOINED:")) {
            parseMeetingPeerEvent(content.substring("MEETING_JOINED:".length()),
                    meetingController::onParticipantJoined);

        } else if (content.startsWith("MEETING_LEFT:")) {
            parseMeetingPeerEvent(content.substring("MEETING_LEFT:".length()),
                    meetingController::onParticipantLeft);

        } else if (content.startsWith("MEETING_ROSTER:")) {
            String rest = content.substring("MEETING_ROSTER:".length());
            String[] p = rest.split("\\|\\|\\|", 2);
            if (p.length == 2) {
                meetingController.onMeetingRoster(p[0].trim(), p[1]);
            }
        }
    }

    /** Parse MEETING_JOINED / MEETING_LEFT : meetingId|||username ou meetingId:username */
    private void parseMeetingPeerEvent(String rest,
                                       java.util.function.BiConsumer<String, String> handler) {
        if (rest == null || rest.isBlank()) return;
        String meetingId;
        String peer;
        if (rest.contains("|||")) {
            String[] p = rest.split("\\|\\|\\|", 2);
            if (p.length < 2) return;
            meetingId = p[0].trim();
            peer = p[1].trim();
        } else {
            int sep = rest.lastIndexOf(':');
            if (sep <= 0 || sep >= rest.length() - 1) return;
            meetingId = rest.substring(0, sep).trim();
            peer = rest.substring(sep + 1).trim();
        }
        handler.accept(meetingId, peer);
    }

    private void handleError(String content) {
        if (content == null) return;
        if (content.startsWith("MEETING_ERROR:")) {
            System.err.println("[Meeting ERROR] " + content.substring("MEETING_ERROR:".length()));
        } else if (content.startsWith("Vous avez été retiré")) {
            // Notifier l'utilisateur qu'il a été retiré d'un groupe
            Platform.runLater(() -> {
                javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING);
                a.setTitle("Groupe"); a.setContentText(content); a.showAndWait();
            });
        }
    }

    @Override
    public void onCallRequest(CallRequest req) {
        Platform.runLater(() -> {
            if (chatView != null) chatView.addContact(req.getCaller());
            callController.onIncomingCall(req);
        });
    }

    @Override
    public void onDisconnected() {
        if (socketManager != null && socketManager.isIntentionalDisconnect()) return;
        if (!reconnectScheduled.compareAndSet(false, true)) return;

        Platform.runLater(() -> {
            if (meetingController.isInMeeting()) meetingController.hangUp();
            callController.onServerLost();
            if (chatView != null) {
                chatView.setConnectionStatus(false, "Connexion perdue — reconnexion au serveur…");
            }
        });
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        Thread t = new Thread(() -> {
            int attempt = 0;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    long delay = Math.min(1500L + attempt * 500L, 5000L);
                    Thread.sleep(delay);
                    if (socketManager == null || socketManager.isIntentionalDisconnect()) return;
                    socketManager.reconnect();
                    Platform.runLater(this::onReconnected);
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception ignored) {
                    attempt++;
                }
            }
        }, "server-reconnect");
        t.setDaemon(true);
        t.start();
    }

    private void onReconnected() {
        reconnectScheduled.set(false);
        int flushed = sender.flushPending();
        if (chatView != null) {
            String msg = flushed > 0
                    ? "Reconnecté — " + flushed + " message(s) envoyé(s)"
                    : "Reconnecté au serveur";
            chatView.setConnectionStatus(true, msg);
            chatView.refreshAfterReconnect();
        }
        loadContacts();
        groupController.requestGroupList();
        loadAllCallHistory();
    }

    public int getPendingCount() {
        return sender != null ? sender.pendingCount() : 0;
    }

    public String getUsername() { return username; }
}
