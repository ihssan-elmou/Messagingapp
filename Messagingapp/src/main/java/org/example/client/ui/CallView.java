package org.example.client.ui;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.*;
import javafx.util.Duration;
import org.example.client.controller.CallController;
import org.example.model.CallRequest;

/**
 * CallView V3 — style WhatsApp réel.
 * Audio : avatar + nom + timer + boutons micro/haut-parleur/raccrocher
 * Vidéo : flux distant (grande) + aperçu local (coin bas-droit) + même boutons
 */
public class CallView {

    private Stage                stage;
    private CallController       controller;
    private String               peer;
    private CallRequest.CallType callType;
    private boolean              isIncoming;

    private final ImageView remoteVideo = new ImageView();
    private final ImageView localVideo  = new ImageView();
    private Label statusLabel;
    private Label timerLabel;
    private boolean muted    = false;
    private boolean camOff   = false;
    private Runnable onMuteToggle;
    private Runnable onCamToggle;

    // Timer
    private int seconds = 0;
    private boolean timerRunning = false;

    public CallView(String peer, CallRequest.CallType type,
                    CallController controller, boolean isIncoming) {
        this.peer       = peer;
        this.callType   = type;
        this.controller = controller;
        this.isIncoming = isIncoming;
    }

    public void setOnMuteToggle(Runnable r) { this.onMuteToggle = r; }
    public void setOnCamToggle(Runnable r)  { this.onCamToggle  = r; }

    public void show() {
        stage = new Stage();
        boolean isVideo = callType == CallRequest.CallType.VIDEO;
        stage.setTitle(isVideo ? "Appel vidéo — " + peer : "Appel vocal — " + peer);
        stage.initModality(Modality.NONE);
        stage.setAlwaysOnTop(true);
        stage.setResizable(isVideo);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color:#111827;");

        // ── Header avec timer ────────────────────────────────────────────
        HBox header = new HBox(12);
        header.setStyle("-fx-background-color:#1F2937;-fx-padding:12 20;");
        header.setAlignment(Pos.CENTER_LEFT);
        String typeIcon = isVideo ? "📹" : "📞";
        Label typeL = new Label(typeIcon);
        typeL.setStyle("-fx-font-size:18px;");
        timerLabel = new Label("00:00");
        timerLabel.setStyle("-fx-text-fill:#F9A8D4;-fx-font-size:15px;-fx-font-weight:bold;");
        statusLabel = new Label(isIncoming ? "Appel entrant..." : "Appel en cours...");
        statusLabel.setStyle("-fx-text-fill:#9CA3AF;-fx-font-size:12px;");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        header.getChildren().addAll(typeL, timerLabel, sp, statusLabel);
        root.setTop(header);

        // ── Centre ───────────────────────────────────────────────────────
        if (isVideo) {
            root.setCenter(buildVideoCenter());
        } else {
            root.setCenter(buildVoiceCenter());
        }

        // ── Boutons ──────────────────────────────────────────────────────
        root.setBottom(buildControls(isVideo));

        int w = isVideo ? 700 : 380;
        int h = isVideo ? 560 : 460;
        Scene scene = new Scene(root, w, h);
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> controller.endCall());
        stage.show();
    }

    // ── Écran vocal ──────────────────────────────────────────────────────
    private VBox buildVoiceCenter() {
        VBox v = new VBox(20);
        v.setAlignment(Pos.CENTER);
        v.setStyle("-fx-background-color:#111827;-fx-padding:40;");

        // Avatar avec animation pulse
        String init = peer.isEmpty() ? "?" : peer.substring(0,1).toUpperCase();
        String[] cols = {"#7C3AED","#DB2777","#2563EB","#059669","#D97706"};
        String col = cols[Math.abs(peer.hashCode()) % cols.length];

        StackPane ring = new StackPane();
        ring.setMinSize(130, 130); ring.setMaxSize(130, 130);
        ring.setStyle("-fx-background-color:rgba(124,58,237,0.2);-fx-background-radius:65;");

        Label av = new Label(init);
        av.setStyle("-fx-background-color:"+col+";-fx-text-fill:white;-fx-font-weight:bold;" +
                "-fx-font-size:42px;-fx-min-width:110px;-fx-min-height:110px;" +
                "-fx-max-width:110px;-fx-max-height:110px;-fx-background-radius:55;-fx-alignment:center;");
        ring.getChildren().add(av);

        ScaleTransition pulse = new ScaleTransition(Duration.millis(1000), ring);
        pulse.setFromX(1.0); pulse.setToX(1.1); pulse.setFromY(1.0); pulse.setToY(1.1);
        pulse.setAutoReverse(true); pulse.setCycleCount(Animation.INDEFINITE); pulse.play();

        Label nameL = new Label(peer);
        nameL.setStyle("-fx-text-fill:#E9EDEF;-fx-font-size:24px;-fx-font-weight:bold;");

        v.getChildren().addAll(ring, nameL);
        return v;
    }

    // ── Écran vidéo ──────────────────────────────────────────────────────
    private StackPane buildVideoCenter() {
        StackPane stack = new StackPane();
        stack.setStyle("-fx-background-color:#0B1117;");

        // Flux distant — plein écran
        remoteVideo.setFitWidth(700); remoteVideo.setFitHeight(440);
        remoteVideo.setPreserveRatio(true);

        // Placeholder quand pas de vidéo distante
        String init = peer.isEmpty() ? "?" : peer.substring(0,1).toUpperCase();
        Label placeholder = new Label(init);
        placeholder.setStyle("-fx-background-color:#374151;-fx-text-fill:white;-fx-font-weight:bold;" +
                "-fx-font-size:48px;-fx-min-width:100px;-fx-min-height:100px;" +
                "-fx-max-width:100px;-fx-max-height:100px;-fx-background-radius:50;-fx-alignment:center;");
        Label peerNameV = new Label(peer);
        peerNameV.setStyle("-fx-text-fill:#9CA3AF;-fx-font-size:16px;");
        VBox remotePlaceholder = new VBox(12, placeholder, peerNameV);
        remotePlaceholder.setAlignment(Pos.CENTER);

        stack.getChildren().addAll(remotePlaceholder, remoteVideo);
        // Bind placeholder visibility
        remoteVideo.imageProperty().addListener((obs, o, img) -> {
            boolean hasImg = img != null && !img.isError();
            remotePlaceholder.setVisible(!hasImg);
        });

        // Mon aperçu local — coin bas-droit
        StackPane localPane = new StackPane();
        localPane.setStyle("-fx-background-color:#1F2937;-fx-background-radius:8;");
        localPane.setMinSize(160, 100); localPane.setMaxSize(160, 100);
        localVideo.setFitWidth(160); localVideo.setFitHeight(100); localVideo.setPreserveRatio(true);
        Label meL = new Label("Moi");
        meL.setStyle("-fx-text-fill:white;-fx-font-size:10px;-fx-padding:2 6;-fx-background-color:rgba(0,0,0,0.5);-fx-background-radius:4;");
        StackPane.setAlignment(meL, Pos.BOTTOM_LEFT);
        StackPane.setMargin(meL, new Insets(0,0,4,4));
        localPane.getChildren().addAll(localVideo, meL);

        StackPane.setAlignment(localPane, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(localPane, new Insets(0, 12, 12, 0));
        stack.getChildren().add(localPane);

        return stack;
    }

    // ── Barre de boutons ─────────────────────────────────────────────────
    private HBox buildControls(boolean isVideo) {
        HBox bar = new HBox(20);
        bar.setStyle("-fx-background-color:#1F2937;-fx-padding:16 30;" +
                "-fx-border-color:#F9A8D4;-fx-border-width:1 0 0 0;");
        bar.setAlignment(Pos.CENTER);

        Button muteBtn = circleBtn("🎤", "#374151", 56);
        muteBtn.setTooltip(new Tooltip("Couper le micro"));
        muteBtn.setOnAction(e -> {
            muted = !muted;
            muteBtn.setText(muted ? "🔇" : "🎤");
            muteBtn.setStyle(muteBtn.getStyle().replace(muted?"#374151":"#6B7280", muted?"#6B7280":"#374151"));
            if (onMuteToggle != null) onMuteToggle.run();
        });

        Button endBtn = circleBtn("📵", "#DC2626", 64);
        endBtn.setTooltip(new Tooltip("Raccrocher"));
        endBtn.setOnAction(e -> { controller.endCall(); stage.close(); });

        Region sl = new Region(); HBox.setHgrow(sl, Priority.ALWAYS);
        Region sr = new Region(); HBox.setHgrow(sr, Priority.ALWAYS);

        if (isVideo) {
            Button camBtn = circleBtn("📷", "#374151", 56);
            camBtn.setTooltip(new Tooltip("Caméra"));
            camBtn.setOnAction(e -> {
                camOff = !camOff;
                camBtn.setText(camOff ? "📵" : "📷");
                if (onCamToggle != null) onCamToggle.run();
            });
            bar.getChildren().addAll(sl, muteBtn, camBtn, endBtn, sr);
        } else {
            bar.getChildren().addAll(sl, muteBtn, endBtn, sr);
        }
        return bar;
    }

    private Button circleBtn(String icon, String bg, int size) {
        Button b = new Button(icon);
        b.setStyle("-fx-background-color:"+bg+";-fx-text-fill:white;" +
                "-fx-font-size:"+(size/3)+"px;" +
                "-fx-min-width:"+size+"px;-fx-min-height:"+size+"px;" +
                "-fx-max-width:"+size+"px;-fx-max-height:"+size+"px;" +
                "-fx-background-radius:"+(size/2)+"px;-fx-cursor:hand;-fx-border-color:transparent;");
        return b;
    }

    // ── Timer ─────────────────────────────────────────────────────────────
    public void startTimer() {
        timerRunning = true;
        Thread t = new Thread(() -> {
            while (timerRunning) {
                try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
                seconds++;
                final int s = seconds;
                Platform.runLater(() -> {
                    if (timerLabel != null) timerLabel.setText(String.format("%02d:%02d", s/60, s%60));
                });
            }
        }, "call-timer");
        t.setDaemon(true); t.start();
    }

    public void setStatus(String text) {
        if (statusLabel != null) Platform.runLater(() -> statusLabel.setText(text));
    }

    public ImageView getVideoView()      { return remoteVideo; }
    public ImageView getLocalVideoView() { return localVideo; }

    public void close() {
        timerRunning = false;
        if (stage != null) Platform.runLater(stage::close);
    }
}
