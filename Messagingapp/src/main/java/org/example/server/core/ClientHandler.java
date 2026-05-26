package org.example.server.core;

import org.example.model.CallRequest;
import org.example.protocol.*;
import org.example.protocol.GroupFileMessage;
import org.example.server.database.dao.CallDAO;
import org.example.server.database.dao.ContactDAO;
import org.example.server.database.dao.FileDAO;
import org.example.server.database.dao.GroupDAO;
import org.example.server.database.dao.MessageDAO;
import org.example.server.database.dao.UserDAO;
import org.example.server.database.entity.DbCall;
import org.example.server.database.entity.DbContact;
import org.example.server.database.entity.DbFile;
import org.example.server.database.entity.DbMessage;
import org.example.server.database.entity.DbUser;
import org.example.server.network.MediaRelay;
import org.example.server.service.*;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Version 2 : ajout du routage MEETING_* et GROUP_LIST.
 */
public class ClientHandler implements Runnable {

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;
    private String             username;

    private final Map<String, ClientHandler> clients;
    private final GroupManager               groupManager;
    private final MediaRelay                 audioRelay;
    private final MediaRelay                 videoRelay;

    private AuthService    authService;
    private ChatService    chatService;
    private CallService    callService;
    private GroupService   groupService;
    private MeetingService meetingService;   // ← V2

    private final UserDAO    userDAO    = new UserDAO();
    private final ContactDAO contactDAO = new ContactDAO();
    private final FileDAO    fileDAO    = new FileDAO();
    private final MessageDAO messageDAO = new MessageDAO();
    private final CallDAO    callDAO    = new CallDAO();
    private final GroupDAO   groupDAO   = new GroupDAO();

    public ClientHandler(Socket socket,
                         Map<String, ClientHandler> clients,
                         GroupManager groupManager,
                         MediaRelay audioRelay,
                         MediaRelay videoRelay,
                         MeetingService meetingService) {
        this.socket         = socket;
        this.clients        = clients;
        this.groupManager   = groupManager;
        this.audioRelay     = audioRelay;
        this.videoRelay     = videoRelay;
        this.meetingService = meetingService;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in  = new ObjectInputStream(socket.getInputStream());

            authService    = new AuthService(clients, this);
            chatService    = new ChatService(clients);
            callService    = new CallService(clients, audioRelay, videoRelay);
            groupService   = new GroupService(clients, groupManager);
            Object first = in.readObject();
            if (first instanceof Message loginMsg) {
                String pwd = loginMsg.getContent() != null ? loginMsg.getContent() : "";
                if (loginMsg.getType() == MessageType.LOGIN) {
                    this.username = authService.login(loginMsg.getSender(), pwd);
                } else if (loginMsg.getType() == MessageType.REGISTER) {
                    this.username = authService.register(loginMsg.getSender(), pwd);
                }
                if (this.username != null) {
                    System.out.println("[LOGIN] Utilisateur connecté : " + this.username);
                    userDAO.updateStatus(this.username, "online");
                    sendContactsHistory();
                } else {
                    try { socket.close(); } catch (IOException ignored) {}
                    return;
                }
            } else {
                try { socket.close(); } catch (IOException ignored) {}
                return;
            }

            Object obj;
            while ((obj = in.readObject()) != null) {
                if      (obj instanceof GroupFileMessage) groupService.handleGroupFile((GroupFileMessage) obj); // V2
                else if (obj instanceof FileMessage)  chatService.forwardFile((FileMessage) obj, clients);
                else if (obj instanceof CallRequest)  callService.handleCallRequest((CallRequest) obj);
                else if (obj instanceof Message)      route((Message) obj);
            }

        } catch (EOFException | SocketException e) {
            System.out.println((username != null ? username : "?") + " déconnecté.");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            disconnect();
        }
    }

    // ------------------------------------------------------------------ //
    //  Routage
    // ------------------------------------------------------------------ //

    private void route(Message msg) {
        switch (msg.getType()) {

            case TEXT:
                chatService.sendPrivate(msg);
                break;

            case DELETE_MESSAGE:
                if (msg.getContent() != null && msg.getContent().startsWith("PRIVATE|")) {
                    chatService.deletePrivateMessage(msg);
                } else if (msg.getContent() != null && msg.getContent().startsWith("GROUP|")) {
                    groupService.deleteGroupMessage(msg);
                }
                break;

            case LOAD_CONTACTS:
                sendContactsHistory();
                break;

            case LOAD_HISTORY:
                sendMessagesHistory(msg.getReceiver());
                break;

            case LOAD_CALL_HISTORY:
                sendCallsHistory(msg.getReceiver());
                break;
            case LOAD_CALL_HISTORY_ALL:
                sendAllCallsHistory();
                break;

            case CALL_ACCEPT:
            case CALL_REJECT:
            case CALL_END:
            case AUDIO_PORT_REGISTER:
            case VIDEO_PORT_REGISTER:
                callService.handleCallSignal(msg);
                break;

            // ---- Groupes ----
            case GROUP_CREATE:
            case GROUP_ADD_MEMBER:
            case GROUP_REMOVE_MEMBER:
            case GROUP_MESSAGE:
            case GROUP_LIST:
            case LOAD_GROUP_HISTORY:   // V2
                groupService.handle(msg);
                break;

            // ---- Réunions V2 ----
            case MEETING_START:
            case MEETING_JOIN:
            case MEETING_LEAVE:
                meetingService.handle(msg);
                break;

            case LOGOUT:
                disconnect();
                break;

            default:
                break;
        }
    }

    // ------------------------------------------------------------------ //
    //  Historiques
    // ------------------------------------------------------------------ //

    private void sendContactsHistory() {
        if (username == null) return;
        List<DbContact> contacts = contactDAO.findContactsOf(username);
        StringBuilder content = new StringBuilder();
        for (DbContact c : contacts) {
            String peer = c.getContactUsername();
            if (peer == null || peer.isBlank() || peer.equals(username)) continue;

            String preview = "";
            String time    = normalizeDbTimestamp(c.getLastInteractionAt());
            int unread     = messageDAO.countUnreadFrom(username, peer);

            DbMessage lastMsg = null;
            if (c.getLastMessageId() != null) {
                lastMsg = messageDAO.findById(c.getLastMessageId());
            }
            if (lastMsg == null) {
                lastMsg = messageDAO.findLastPrivateMessage(username, peer);
            }
            if (lastMsg != null) {
                time = normalizeDbTimestamp(lastMsg.getSentAt());
                boolean fromMe = username.equals(lastMsg.getSender());
                String text = lastMsg.getContent() != null ? lastMsg.getContent() : "";
                preview = fromMe ? "Vous: " + text : text;
            } else {
                DbFile lastFile = fileDAO.findLastFileBetween(username, peer);
                if (lastFile != null) {
                    time = normalizeDbTimestamp(lastFile.getUploadedAt());
                    boolean fromMe = username.equals(lastFile.getSender());
                    String fn = lastFile.getFileName() != null ? lastFile.getFileName() : "fichier";
                    String ft = lastFile.getFileType() != null ? lastFile.getFileType() : "";
                    String filePrev = ft.startsWith("image") ? "📷 Photo" : "📎 " + fn;
                    preview = fromMe ? "Vous: " + filePrev : filePrev;
                }
            }

            content.append(peer).append('\t')
                   .append(encode(preview)).append('\t')
                   .append(nullToEmpty(time)).append('\t')
                   .append(unread).append('\n');
        }
        sendObject(new Message(MessageType.CONTACTS_RESPONSE, "SERVER", username, content.toString()));
    }

    private void sendMessagesHistory(String peer) {
        if (username == null || peer == null || peer.isBlank()) return;
        messageDAO.markConversationSeen(username, peer);

        // 1. Messages texte
        List<DbMessage> messages = messageDAO.findConversation(username, peer);
        StringBuilder content = new StringBuilder();
        for (DbMessage m : messages) {
            content.append(m.getId()).append('\t')
                   .append(nullToEmpty(m.getSender())).append('\t')
                   .append(nullToEmpty(m.getReceiver())).append('\t')
                   .append(nullToEmpty(normalizeDbTimestamp(m.getSentAt()))).append('\t')
                   .append(encode(m.getContent()))
                   .append('\n');
        }
        sendObject(new Message(MessageType.HISTORY_RESPONSE, "SERVER", peer, content.toString()));

        // 2. Fichiers prives : avec uploadedAt réel pour tri chronologique côté client
        List<DbFile> files = fileDAO.findFilesBetween(username, peer);
        for (DbFile f : files) {
            if (f.getData() == null || f.getData().length == 0) continue;
            sendObject(new FileMessage(
                    f.getSender(), f.getReceiver(),
                    f.getFileName(), f.getFileType(), f.getData(),
                    normalizeDbTimestamp(f.getUploadedAt())));
        }
    }

    private void sendCallsHistory(String peer) {
        if (username == null || peer == null || peer.isBlank()) return;
        List<DbCall> calls = callDAO.findCallsBetween(username, peer);
        StringBuilder content = new StringBuilder();
        for (DbCall c : calls) {
            content.append(peer).append('\t')
                   .append(nullToEmpty(c.getCaller())).append('\t')
                   .append(nullToEmpty(c.getCallee())).append('\t')
                   .append(nullToEmpty(c.getCallType())).append('\t')
                   .append(nullToEmpty(c.getCallStatus())).append('\t')
                   .append(nullToEmpty(c.getStartedAt())).append('\t')
                   .append(c.getDurationSeconds())
                   .append('\n');
        }
        sendObject(new Message(MessageType.CALL_HISTORY_RESPONSE, "SERVER", peer, content.toString()));
    }

    // ------------------------------------------------------------------ //
    //  I/O
    // ------------------------------------------------------------------ //

    public synchronized void sendObject(Object obj) {
        try {
            out.writeObject(obj);
            out.flush();
            out.reset();
        } catch (IOException e) {
            System.err.println("Erreur envoi vers " + username + " : " + e.getMessage());
        }
    }

    private void sendAllCallsHistory() {
        if (username == null) return;
        List<org.example.server.database.entity.DbCall> calls = callDAO.findCallHistory(username);
        StringBuilder sb = new StringBuilder();
        for (org.example.server.database.entity.DbCall c : calls) {
            String peer;
            if (c.getGroupId() != null && !c.getGroupId().isBlank()) {
                GroupDAO.GroupRow gr = groupDAO.findById(c.getGroupId());
                peer = "👥 " + (gr != null ? gr.name : c.getGroupId());
            } else {
                peer = username.equals(c.getCaller()) ? nullToEmpty(c.getCallee()) : nullToEmpty(c.getCaller());
            }
            sb.append(peer).append('\t')
              .append(nullToEmpty(c.getCaller())).append('\t')
              .append(nullToEmpty(c.getCallee())).append('\t')
              .append(nullToEmpty(c.getCallType())).append('\t')
              .append(nullToEmpty(c.getCallStatus())).append('\t')
              .append(nullToEmpty(c.getStartedAt())).append('\t')
              .append(c.getDurationSeconds()).append('\n');
        }
        sendObject(new Message(MessageType.CALL_HISTORY_ALL_RESPONSE, "SERVER", username, sb.toString()));
    }

    private void disconnect() {
        if (username != null) {
            String old = username;
            if (meetingService != null) meetingService.leaveAllMeetingsFor(old);
            try { userDAO.updateStatus(old, "offline"); } catch (Exception ignored) {}
            clients.remove(old);
            new AuthService(clients, this).broadcastUserList();
            username = null;
        }
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException ignored) {}
    }

    // ------------------------------------------------------------------ //
    //  Utilitaires
    // ------------------------------------------------------------------ //

    private String encode(String text) {
        if (text == null) text = "";
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private String nullToEmpty(String v) { return v == null ? "" : v; }

    /** Format uniforme yyyy-MM-dd HH:mm:ss pour tri chronologique côté client. */
    private static String normalizeDbTimestamp(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        String s = raw.trim().replace('T', ' ');
        int dot = s.indexOf('.');
        if (dot > 0) s = s.substring(0, dot);
        if (s.length() > 19) s = s.substring(0, 19);
        return s;
    }

    public InetAddress getClientAddress() { return socket.getInetAddress(); }
    public String      getUsername()      { return username; }
    public void        setUsername(String u) { this.username = u; }
}
