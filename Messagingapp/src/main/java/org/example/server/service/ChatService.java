package org.example.server.service;

import org.example.protocol.*;
import org.example.server.core.ClientHandler;
import org.example.server.database.dao.FileDAO;
import org.example.server.database.dao.MessageDAO;
import org.example.server.database.entity.DbFile;
import org.example.server.database.entity.DbMessage;

import java.util.Map;

public class ChatService {

    private Map<String, ClientHandler> clients;
    private MessageDAO messageDAO = new MessageDAO();
    private FileDAO fileDAO = new FileDAO();

    public ChatService(Map<String, ClientHandler> clients) {
        this.clients = clients;
    }

    public void sendPrivate(Message msg) {

        messageDAO.create(new DbMessage(
                msg.getSender(),
                msg.getReceiver(),
                msg.getContent()
        ));

        // Envoyer UNIQUEMENT au destinataire (pas à l'expéditeur)
        if (!msg.getSender().equals(msg.getReceiver())) {
            ClientHandler target = clients.get(msg.getReceiver());
            if (target != null) {
                target.sendObject(msg);
                System.out.println("[CHAT] " + msg.getSender()
                        + " -> " + msg.getReceiver() + " : " + msg.getContent());
            } else {
                System.out.println("[CHAT] Destinataire hors ligne : " + msg.getReceiver());
            }
        }

    }

    public void deletePrivateMessage(Message msg) {
        if (msg == null || msg.getContent() == null) return;
        String[] p = msg.getContent().split("\\|", 3);
        if (p.length < 3) return;
        int messageId;
        try { messageId = Integer.parseInt(p[1]); } catch (NumberFormatException e) { return; }
        String peer = p[2];

        DbMessage db = messageDAO.findById(messageId);
        if (db == null || !msg.getSender().equals(db.getSender())) return;
        if (db.getGroupId() != null) return;

        messageDAO.delete(messageId);
        String payload = "PRIVATE|" + messageId + "|" + peer;
        Message notif = new Message(MessageType.DELETE_MESSAGE, msg.getSender(), peer, payload);
        ClientHandler target = clients.get(peer);
        if (target != null) target.sendObject(notif);
        ClientHandler self = clients.get(msg.getSender());
        if (self != null) self.sendObject(notif);
    }

    public void forwardFile(FileMessage fm, Map<String, ClientHandler> clients) {
        fileDAO.create(new DbFile(
                fm.getSender(),
                fm.getReceiver(),
                fm.getFileName(),
                fm.getFileType(),
                fm.getFileSize(),
                fm.getData()
        ));

        ClientHandler target = clients.get(fm.getReceiver());
        if (target != null) {
            target.sendObject(fm);
            System.out.println("[FILE] " + fm.getSender()
                    + " -> " + fm.getReceiver() + " : " + fm.getFileName());
        }
    }
}
