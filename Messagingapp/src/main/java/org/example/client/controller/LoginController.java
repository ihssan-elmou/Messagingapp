package org.example.client.controller;

import org.example.client.network.*;
import org.example.client.ui.ChatView;
import org.example.utils.Constants;
import javafx.stage.Stage;

import java.io.IOException;

public class LoginController {

    private Stage primaryStage;

    public LoginController(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    /**
     * Appelé par LoginView quand l'utilisateur valide.
     * @throws IOException si la connexion échoue
     */
    public void onLogin(String username, String password, String host, boolean register)
            throws IOException {
        ChatController chatController = new ChatController(username);

        ClientSocketManager socketManager = new ClientSocketManager(
                username, password, host, Constants.TEXT_PORT, chatController, register);

        chatController.setCanonicalUsername(socketManager.getUsername());
        chatController.init(socketManager);

        // Passer à la vue principale
        ChatView chatView = new ChatView(chatController, primaryStage);
        chatController.setChatView(chatView);
        chatView.show();
    }
}