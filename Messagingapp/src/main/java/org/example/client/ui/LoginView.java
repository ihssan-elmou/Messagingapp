package org.example.client.ui;

import javafx.application.Application;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.example.client.controller.LoginController;
import org.example.utils.Constants;

public class LoginView extends Application {

    private static final String BG_DARK   = "#111B21";
    private static final String BG_PANEL  = "#202C33";
    private static final String BG_INPUT  = "#2A3942";
    private static final String TXT_WHITE = "#E9EDEF";
    private static final String TXT_GRAY  = "#8696A0";
    private static final String GREEN     = "#00A884";

    @Override
    public void start(Stage primaryStage) {
        LoginController controller = new LoginController(primaryStage);

        TextField usernameField = new TextField();
        usernameField.setPromptText("user name");
        usernameField.getStyleClass().add("wa-login-field");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Mot de passe");
        passwordField.getStyleClass().add("wa-login-field");

        TextField serverField = new TextField(Constants.SERVER_HOST);
        serverField.getStyleClass().add("wa-login-field");

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #EA0038; -fx-font-size: 12px; -fx-wrap-text: true;");
        errorLabel.setMaxWidth(340);

        ToggleGroup modeGroup = new ToggleGroup();
        RadioButton loginMode    = new RadioButton("Se connecter");
        RadioButton registerMode = new RadioButton("S'inscrire");
        loginMode.setToggleGroup(modeGroup);
        registerMode.setToggleGroup(modeGroup);
        loginMode.setSelected(true);
        styleRadio(loginMode);
        styleRadio(registerMode);

        Button actionBtn = new Button("Se connecter →");
        actionBtn.getStyleClass().add("wa-btn-primary");

        modeGroup.selectedToggleProperty().addListener((obs, o, sel) -> {
            boolean reg = sel == registerMode;
            actionBtn.setText(reg ? "Créer mon compte →" : "Se connecter →");
        });

        Runnable doAction = () -> {
            String username = usernameField.getText().trim();
            String password = passwordField.getText();
            String host     = serverField.getText().trim();
            boolean register = registerMode.isSelected();

            if (username.isEmpty()) {
                errorLabel.setText("Entrez un nom d'utilisateur.");
                return;
            }
            if (password.isEmpty()) {
                errorLabel.setText("Entrez un mot de passe.");
                return;
            }
            try {
                controller.onLogin(username, password, host, register);
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "Connexion impossible.";
                errorLabel.setText(msg);
            }
        };

        actionBtn.setOnAction(e -> doAction.run());
        passwordField.setOnAction(e -> doAction.run());
        usernameField.setOnAction(e -> doAction.run());

        VBox card = new VBox(16);
        card.getStyleClass().add("wa-login-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMaxWidth(400);

        Text title = new Text("WhatsApp");
        title.getStyleClass().add("wa-login-title");
        Label subtitle = new Label("Connectez-vous ou créez un compte");
        subtitle.getStyleClass().add("wa-login-subtitle");

        Label lblUser = label("NOM D'UTILISATEUR");
        Label lblPass = label("MOT DE PASSE");
        Label lblServer = label("SERVEUR");

        HBox modeRow = new HBox(24, loginMode, registerMode);
        modeRow.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(
                title, subtitle,
                new Separator(),
                modeRow,
                new VBox(4, lblUser, usernameField),
                new VBox(4, lblPass, passwordField),
                new VBox(4, lblServer, serverField),
                errorLabel,
                actionBtn);

        StackPane root = new StackPane(card);
        root.getStyleClass().add("wa-login-root");
        root.setPadding(new Insets(40));

        Scene scene = new Scene(root, 480, 560);
        scene.getStylesheets().add(
                getClass().getResource("/org/example/styles.css").toExternalForm());
        primaryStage.setTitle("WhatsApp — Connexion");
        primaryStage.setScene(scene);
        primaryStage.setResizable(true);
        primaryStage.show();
    }

    private Label label(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("wa-login-label");
        return l;
    }

    private void styleRadio(RadioButton rb) {
        rb.setStyle("-fx-text-fill:" + TXT_WHITE + ";-fx-font-size:13px;");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
