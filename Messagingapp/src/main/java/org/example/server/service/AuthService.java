package org.example.server.service;

import org.example.protocol.*;
import org.example.server.core.ClientHandler;
import org.example.server.database.dao.UserDAO;
import org.example.server.database.entity.DbUser;
import org.example.utils.PasswordUtils;

import java.util.Map;

public class AuthService {

    private final Map<String, ClientHandler> clients;
    private final ClientHandler              self;
    private final UserDAO                    userDAO = new UserDAO();

    public AuthService(Map<String, ClientHandler> clients, ClientHandler self) {
        this.clients = clients;
        this.self    = self;
    }

    /**
     * @return null si échec (mot de passe dans {@code password})
     */
    public String login(String username, String password) {
        String login = username != null ? username.trim() : "";
        if (login.isEmpty()) return null;

        DbUser db = userDAO.findByLoginNameAndPassword(login, password);
        if (db == null) {
            if (!userDAO.findLoginCandidates(login).isEmpty()) {
                self.sendObject(new Message(MessageType.ERROR, "SERVER", login,
                        "AUTH: Mot de passe incorrect."));
            } else {
                self.sendObject(new Message(MessageType.ERROR, "SERVER", login,
                        "AUTH: Utilisateur inconnu. Inscrivez-vous d'abord."));
            }
            return null;
        }

        return completeLogin(db.getUsername());
    }

    /**
     * @return null si échec
     */
    public String register(String username, String password) {
        String display = username != null ? username.trim() : "";
        if (display.isEmpty()) return null;
        if (password == null || password.length() < 1) {
            self.sendObject(new Message(MessageType.ERROR, "SERVER", display,
                    "AUTH: Choisissez un mot de passe."));
            return null;
        }

        if (userDAO.existsWithDisplayNameAndPassword(display, password)) {
            self.sendObject(new Message(MessageType.ERROR, "SERVER", display,
                    "AUTH: Ce nom et ce mot de passe sont déjà inscrits. Connectez-vous."));
            return null;
        }

        String internal = userDAO.allocateUsername(display);
        userDAO.create(new DbUser(internal, display, "offline"));
        userDAO.setPasswordHash(internal, PasswordUtils.hash(password));
        return completeLogin(internal);
    }

    private String completeLogin(String username) {
        clients.put(username, self);
        self.setUsername(username);
        String display = userDAO.getDisplayName(username);
        self.sendObject(new Message(MessageType.INFO, "SERVER", username, "AUTH_OK:" + display));
        System.out.println("[AUTH] " + username + " connecté (affiché : " + display + ").");
        broadcastUserList();
        return username;
    }

    public void broadcastUserList() {
        String csv = String.join(",", clients.keySet());
        Message msg = new Message(MessageType.USER_LIST, "SERVER", null, csv);
        for (ClientHandler ch : clients.values()) {
            ch.sendObject(msg);
        }
    }
}
