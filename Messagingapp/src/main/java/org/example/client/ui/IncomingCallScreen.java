package org.example.client.ui;

import javafx.animation.ScaleTransition;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Écran d'appel entrant (style WhatsApp sombre) — partagé entre appels 1-à-1 et réunions de groupe.
 */
public final class IncomingCallScreen {

    private IncomingCallScreen() {}

    public static void show(String windowTitle,
                            String displayName,
                            String subtitle,
                            Runnable onAccept,
                            Runnable onReject) {
        Stage screen = new Stage();
        screen.setTitle(windowTitle);
        screen.initModality(Modality.APPLICATION_MODAL);
        screen.setAlwaysOnTop(true);
        screen.setResizable(false);

        VBox root = new VBox(24);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color:#111827;-fx-padding:40;");

        String name = displayName == null ? "?" : displayName;
        String init = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        String[] cols = {"#7C3AED", "#DB2777", "#2563EB", "#059669", "#D97706"};
        String col = cols[Math.abs(name.hashCode()) % cols.length];

        Label av = new Label(init);
        av.setStyle("-fx-background-color:" + col + ";-fx-text-fill:white;-fx-font-weight:bold;"
                + "-fx-font-size:38px;-fx-min-width:100px;-fx-min-height:100px;"
                + "-fx-max-width:100px;-fx-max-height:100px;-fx-background-radius:50;-fx-alignment:center;");

        ScaleTransition pulse = new ScaleTransition(Duration.millis(900), av);
        pulse.setFromX(1.0);
        pulse.setToX(1.1);
        pulse.setFromY(1.0);
        pulse.setToY(1.1);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(-1);
        pulse.play();

        Label nameL = new Label(name);
        nameL.setStyle("-fx-text-fill:#E9EDEF;-fx-font-size:22px;-fx-font-weight:bold;");

        Label subL = new Label(subtitle == null ? "" : subtitle);
        subL.setWrapText(true);
        subL.setMaxWidth(280);
        subL.setAlignment(Pos.CENTER);
        subL.setStyle("-fx-text-fill:#9CA3AF;-fx-font-size:14px;");

        Button acceptBtn = circleBtn("📞", "#22C55E", 70);
        acceptBtn.setTooltip(new Tooltip("Accepter"));
        Button rejectBtn = circleBtn("📵", "#EF4444", 70);
        rejectBtn.setTooltip(new Tooltip("Refuser"));

        Runnable closeAndStop = () -> {
            pulse.stop();
            screen.close();
        };

        acceptBtn.setOnAction(e -> {
            closeAndStop.run();
            if (onAccept != null) onAccept.run();
        });

        rejectBtn.setOnAction(e -> {
            closeAndStop.run();
            if (onReject != null) onReject.run();
        });

        HBox btns = new HBox(50, rejectBtn, acceptBtn);
        btns.setAlignment(Pos.CENTER);

        root.getChildren().addAll(av, nameL, subL, btns);
        screen.setScene(new Scene(root, 340, 400));
        screen.setOnCloseRequest(e -> {
            closeAndStop.run();
            if (onReject != null) onReject.run();
        });
        screen.show();
    }

    private static Button circleBtn(String icon, String bg, int size) {
        Button b = new Button(icon);
        b.setStyle("-fx-background-color:" + bg + ";-fx-text-fill:white;-fx-font-size:" + (size / 3) + "px;"
                + "-fx-min-width:" + size + "px;-fx-min-height:" + size + "px;"
                + "-fx-max-width:" + size + "px;-fx-max-height:" + size + "px;"
                + "-fx-background-radius:" + (size / 2) + "px;"
                + "-fx-cursor:hand;-fx-border-color:transparent;");
        return b;
    }
}
