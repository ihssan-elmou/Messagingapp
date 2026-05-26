package org.example.client.ui;

import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.example.client.controller.ChatController;

import java.util.List;

/**
 * Vue historique des appels — style WhatsApp.
 * S'ouvre au clic sur l'icône 📞 de la sidebar.
 */
public class CallHistoryView {

    public static class CallEntry {
        public final String peer, type, status, startedAt;
        public final int durationSec;
        public final boolean fromMe;
        public CallEntry(String peer, String type, String status, String startedAt, int durationSec, boolean fromMe) {
            this.peer=peer; this.type=type; this.status=status; this.startedAt=startedAt;
            this.durationSec=durationSec; this.fromMe=fromMe;
        }
    }

    private final Stage          stage;
    private final ChatController controller;
    private final VBox           listBox = new VBox(0);

    public CallHistoryView(ChatController controller, Stage owner) {
        this.controller = controller;
        this.stage      = new Stage();
        buildUI();
    }

    private void buildUI() {
        stage.setTitle("Appels");

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: #FFFFFF;");

        // Header
        HBox header = new HBox();
        header.setStyle("-fx-background-color: #FCE4EC; -fx-padding: 16;");
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("📞  Appels");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #E91E8C;");
        header.getChildren().add(title);

        ScrollPane sp = new ScrollPane(listBox);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: white;");
        VBox.setVgrow(sp, Priority.ALWAYS);

        root.getChildren().addAll(header, sp);

        Scene scene = new Scene(root, 400, 600);
        stage.setScene(scene);
    }

    public void setCallHistory(List<CallEntry> entries) {
        Platform.runLater(() -> {
            listBox.getChildren().clear();
            if (entries.isEmpty()) {
                Label empty = new Label("Aucun appel");
                empty.setStyle("-fx-text-fill: #999; -fx-font-size: 14px; -fx-padding: 20;");
                listBox.getChildren().add(empty);
                return;
            }
            for (CallEntry e : entries) listBox.getChildren().add(buildCallRow(e));
        });
    }

    private HBox buildCallRow(CallEntry e) {
        HBox row = new HBox(14);
        row.setPadding(new Insets(12, 16, 12, 16));
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-border-color: transparent transparent #FCE4EC transparent; -fx-border-width: 0 0 1 0;");

        // Avatar
        String init = e.peer.isEmpty() ? "?" : e.peer.substring(0, 1).toUpperCase();
        Label avatar = new Label(init);
        avatar.setStyle("-fx-background-color: #E91E8C; -fx-text-fill: white; -fx-font-weight: bold;" +
                "-fx-font-size: 16px; -fx-min-width: 46px; -fx-min-height: 46px;" +
                "-fx-max-width: 46px; -fx-max-height: 46px;" +
                "-fx-background-radius: 50%; -fx-alignment: center;");

        // Info
        Label peerLbl = new Label(e.peer);
        peerLbl.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #212121;");

        String statusIcon = switch (e.status == null ? "" : e.status) {
            case "ENDED"    -> e.fromMe ? "↗" : "↙";
            case "MISSED"   -> "↙";
            case "REJECTED" -> "✗";
            default         -> "⏳";
        };
        String typeStr = "VIDEO".equals(e.type) ? " 🎥" : " 📞";
        String dur = e.durationSec > 0 ? "  •  " + formatDur(e.durationSec) : "";
        String statusText = statusIcon + typeStr + "  " + formatDate(e.startedAt) + dur;

        boolean missed = "MISSED".equals(e.status) || "REJECTED".equals(e.status);
        Label statusLbl = new Label(statusText);
        statusLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (missed ? "#E53935" : "#757575") + ";");

        VBox info = new VBox(3, peerLbl, statusLbl);
        HBox.setHgrow(info, Priority.ALWAYS);

        // Bouton rappel
        Button callBtn = new Button("VIDEO".equals(e.type) ? "🎥" : "📞");
        callBtn.setStyle("-fx-background-color: #E91E8C; -fx-text-fill: white;" +
                "-fx-background-radius: 50%; -fx-min-width: 36px; -fx-min-height: 36px;" +
                "-fx-max-width: 36px; -fx-max-height: 36px; -fx-cursor: hand;");
        callBtn.setOnAction(ev -> {
            if ("VIDEO".equals(e.type)) controller.startVideoCall(e.peer);
            else                          controller.startAudioCall(e.peer);
        });

        row.getChildren().addAll(avatar, info, callBtn);
        row.setOnMouseEntered(ev -> row.setStyle(row.getStyle() + "-fx-background-color: #FFF0F5;"));
        row.setOnMouseExited(ev  -> row.setStyle(
                "-fx-border-color: transparent transparent #FCE4EC transparent; -fx-border-width: 0 0 1 0;"));
        return row;
    }

    private String formatDur(int sec) {
        if (sec < 60) return sec + "s";
        return (sec / 60) + "m " + (sec % 60) + "s";
    }

    private String formatDate(String raw) {
        if (raw == null || raw.isBlank()) return "";
        if (raw.length() >= 16) return raw.substring(5, 16).replace("-", "/"); // MM/DD HH:MM
        return raw;
    }

    public void show() { stage.show(); stage.toFront(); }
}
