package org.example.server.network;

import org.example.model.CallRequest;
import org.example.server.core.ClientHandler;

import java.util.Map;

/**
 * Transfère les CallRequest entre clients.
 */
public class CallRouter {

    private Map<String, ClientHandler> clients;

    public CallRouter(Map<String, ClientHandler> clients) {
        this.clients = clients;
    }

    public void forwardCallRequest(CallRequest req) {
        ClientHandler callee = clients.get(req.getCallee());
        if (callee != null) {
            callee.sendObject(req);
        }
    }
}