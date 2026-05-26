package org.example.server.core;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registre central des clients connectés.
 * Utilisé par les services pour accéder aux ClientHandlers.
 */
public class ClientManager {

    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();

    public void register(String username, ClientHandler handler) {
        clients.put(username, handler);
    }

    public void unregister(String username) {
        clients.remove(username);
    }

    public ClientHandler get(String username) {
        return clients.get(username);
    }


    public boolean isOnline(String username) {
        return clients.containsKey(username);
    }

    public Collection<ClientHandler> getAll() {
        return clients.values();
    }

    public Map<String, ClientHandler> getMap() {
        return clients;
    }

    public String getUserListCSV() {
        return String.join(",", clients.keySet());
    }
}