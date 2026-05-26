package org.example.server.network;

import org.example.protocol.Message;
import org.example.server.core.ClientHandler;

import java.util.Map;

/**
 * Route un message vers le bon destinataire.
 */
public class MessageRouter {

    private Map<String, ClientHandler> clients;

    public MessageRouter(Map<String, ClientHandler> clients) {
        this.clients = clients;
    }

    public void route(Message msg) {
        if (msg.getReceiver() == null) {
            broadcast(msg);
        } else {
            sendTo(msg.getReceiver(), msg);
        }
    }

    public void sendTo(String username, Object obj) {
        ClientHandler ch = clients.get(username);
        if (ch != null) ch.sendObject(obj);
    }

    public void broadcast(Object obj) {
        clients.values().forEach(ch -> ch.sendObject(obj));
    }
}