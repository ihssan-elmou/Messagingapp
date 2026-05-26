package org.example.client.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.client.controller.MeetingController;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MeetingView V4 — réunion groupe style WhatsApp.
 * Grille adaptative (1→plein écran, 2→côte à côte, 3-4→2×2, 5-6→3×2, …).
 * Un participant qui raccroche disparaît immédiatement de la grille.
 */
public class MeetingView {

    private static final String BG_MAIN    = "#0B141A";
    private static final String BG_TILE    = "#1A2332";
    private static final String BG_BAR     = "#1F2C34";
    private static final String TXT_MAIN   = "#E9EDEF";
    private static final String TXT_SUB    = "#8696A0";
    private static final String GREEN_ACCENT = "#00A884";
    private static final String TILE_BORDER  = "rgba(255,255,255,0.12)";
    private static final String WA_TILE_BG   = "#202C33";
    /** Couleurs bordure + onde sonore (style WhatsApp). */
    private static final String[] WA_ACCENT = {
            "#25D366", "#FF9800", "#E91E63", "#42A5F5", "#AB47BC", "#26C6DA"
    };

    private final Stage             stage;
    private final String            groupName;
    private final String            myUsername;
    private final MeetingController controller;
    private final boolean           videoMode;

    private final ImageView localVideoView = new ImageView();

    /** Participants actuellement dans l'appel (ordre stable, accès multi-thread). */
    private final Set<String> activeParticipants =
            Collections.synchronizedSet(new LinkedHashSet<>());
    /** Participants partis — plus aucune tuile ni flux vidéo. */
    private final Set<String>           leftParticipants  = new HashSet<>();
    /** username → ImageView distant (thread-safe pour VideoPlayer UDP) */
    private final Map<String, ImageView> remoteViews = new ConcurrentHashMap<>();
    /** username → tuile UI */
    private final Map<String, StackPane>   videoTiles  = new LinkedHashMap<>();
    private final Map<String, StackPane> audioTiles  = new LinkedHashMap<>();
    private final Map<String, Timeline>  waveTimelines = new HashMap<>();

    private GridPane  videoGrid;
    private GridPane  audioGrid;
    private StackPane centerStack;
    private Label     emptyStateLbl;
    private volatile boolean closing;

    private int     secondsElapsed = 0;
    private boolean timerRunning   = false;
    private final Label durationLabel = new Label("00:00");
    private final Label statusLbl     = new Label("");
    private Button muteBtn;

    public MeetingView(String groupName, String myUsername,
                       MeetingController controller, boolean videoMode) {
        this.groupName  = groupName;
        this.myUsername = myUsername;
        this.controller = controller;
        this.videoMode  = videoMode;
        this.stage      = new Stage();
        buildUI();
        startTimer();
    }

    public MeetingView(String groupName, String myUsername, MeetingController controller) {
        this(groupName, myUsername, controller, false);
    }

    // ─────────────────────────────────────────────────────────────
    //  UI
    // ─────────────────────────────────────────────────────────────

    private void buildUI() {
        stage.setTitle((videoMode ? "Appel vidéo" : "Appel vocal") + " — " + groupName);
        stage.setOnCloseRequest(e -> {
            e.consume();
            if (!closing) controller.hangUp();
        });

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + BG_MAIN + ";");
        root.setTop(buildHeader());
        root.setCenter(buildCenter());
        root.setBottom(buildControls());

        int w = videoMode ? 960 : 900;
        int h = videoMode ? 680 : 700;
        Scene scene = new Scene(root, w, h);
        stage.setScene(scene);
        stage.setMinWidth(videoMode ? 640 : 400);
        stage.setMinHeight(480);
    }

    private HBox buildHeader() {
        if (!videoMode) {
            return buildWhatsAppAudioHeader();
        }
        HBox h = new HBox(12);
        h.setAlignment(Pos.CENTER_LEFT);
        h.setPadding(new Insets(14, 20, 14, 20));
        h.setStyle("-fx-background-color: " + BG_BAR + ";");

        Label lock = new Label("🔒");
        lock.setStyle("-fx-font-size: 12px;");
        Label enc = new Label("Chiffrement de bout en bout");
        enc.setStyle("-fx-text-fill: " + TXT_SUB + "; -fx-font-size: 12px;");

        VBox titleBox = new VBox(2);
        Label title = new Label(groupName);
        title.setStyle("-fx-text-fill: " + TXT_MAIN + "; -fx-font-size: 16px; -fx-font-weight: bold;");
        statusLbl.setStyle("-fx-text-fill: " + TXT_SUB + "; -fx-font-size: 12px;");
        titleBox.getChildren().addAll(title, statusLbl);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        durationLabel.setStyle("-fx-text-fill: " + GREEN_ACCENT + "; -fx-font-size: 15px; -fx-font-weight: bold;");

        h.getChildren().addAll(lock, enc, titleBox, sp, durationLabel);
        return h;
    }

    /** En-tête style WhatsApp : chiffrement centré + durée. */
    private HBox buildWhatsAppAudioHeader() {
        HBox h = new HBox();
        h.setAlignment(Pos.CENTER);
        h.setPadding(new Insets(10, 16, 10, 16));
        h.setStyle("-fx-background-color: " + BG_MAIN + ";");

        Label lock = new Label("🔒");
        lock.setStyle("-fx-font-size: 11px;");
        Label enc = new Label("Chiffrement de bout en bout");
        enc.setStyle("-fx-text-fill: " + TXT_SUB + "; -fx-font-size: 12px;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        durationLabel.setStyle("-fx-text-fill: " + TXT_SUB + "; -fx-font-size: 13px;");

        statusLbl.setVisible(false);
        h.getChildren().addAll(lock, enc, sp, durationLabel);
        return h;
    }

    private StackPane buildCenter() {
        centerStack = new StackPane();
        centerStack.setStyle("-fx-background-color: " + BG_MAIN + ";");

        if (videoMode) {
            videoGrid = new GridPane();
            videoGrid.setStyle("-fx-background-color: " + BG_MAIN + ";");
            videoGrid.setHgap(4);
            videoGrid.setVgap(4);
            videoGrid.setPadding(new Insets(4));
            centerStack.getChildren().add(videoGrid);
        } else {
            audioGrid = new GridPane();
            audioGrid.setStyle("-fx-background-color: " + BG_MAIN + ";");
            audioGrid.setHgap(12);
            audioGrid.setVgap(12);
            audioGrid.setPadding(new Insets(16));
            audioGrid.setAlignment(Pos.CENTER);

            emptyStateLbl = new Label("En attente d'autres participants…");
            emptyStateLbl.setStyle("-fx-text-fill: " + TXT_SUB + "; -fx-font-size: 14px;");
            StackPane audioWrap = new StackPane();
            audioWrap.getChildren().addAll(audioGrid, emptyStateLbl);

            ScrollPane sp = new ScrollPane(audioWrap);
            sp.setFitToWidth(true);
            sp.setFitToHeight(true);
            sp.setStyle("-fx-background: " + BG_MAIN + "; -fx-background-color: " + BG_MAIN + ";");
            centerStack.getChildren().add(sp);
        }
        return centerStack;
    }

    private HBox buildControls() {
        HBox bar = new HBox(16);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(16, 24, 20, 24));
        bar.setStyle("-fx-background-color: " + BG_BAR + ";");

        Region sl = new Region(); HBox.setHgrow(sl, Priority.ALWAYS);
        Region sr = new Region(); HBox.setHgrow(sr, Priority.ALWAYS);

        muteBtn = circleBtn("🎤", "#3D4F5C", 52);
        muteBtn.setTooltip(new Tooltip("Micro"));
        muteBtn.setOnAction(e -> {
            controller.toggleMute();
            boolean muted = controller.isAudioMuted();
            muteBtn.setText(muted ? "🔇" : "🎤");
            updateBtnBg(muteBtn, muted ? "#6B7280" : "#3D4F5C");
        });

        final boolean[] camOn = {videoMode};
        Button camBtn = circleBtn("📷", camOn[0] ? GREEN_ACCENT : "#3D4F5C", 52);
        camBtn.setTooltip(new Tooltip("Caméra"));
        camBtn.setOnAction(e -> {
            camOn[0] = !camOn[0];
            if (camOn[0]) {
                controller.startVideo();
                updateBtnBg(camBtn, GREEN_ACCENT);
            } else {
                controller.stopVideo();
                updateBtnBg(camBtn, "#3D4F5C");
                refreshVideoGrid();
            }
        });

        Button hangupBtn = circleBtn("📵", "#E53935", 58);
        hangupBtn.setTooltip(new Tooltip("Raccrocher"));
        hangupBtn.setOnAction(e -> controller.hangUp());

        if (videoMode) {
            bar.getChildren().addAll(sl, muteBtn, camBtn, hangupBtn, sr);
        } else {
            bar.getChildren().addAll(sl, muteBtn, hangupBtn, sr);
        }
        return bar;
    }

    private Button circleBtn(String icon, String bg, int size) {
        Button b = new Button(icon);
        b.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: white; -fx-font-size: "
                + (size / 3) + "px; -fx-min-width: " + size + "px; -fx-min-height: " + size
                + "px; -fx-max-width: " + size + "px; -fx-max-height: " + size
                + "px; -fx-background-radius: " + (size / 2) + "; -fx-cursor: hand; -fx-border-color: transparent;");
        return b;
    }

    private void updateBtnBg(Button b, String color) {
        String s = b.getStyle();
        int i = s.indexOf("-fx-background-color:");
        if (i >= 0) {
            int end = s.indexOf(';', i);
            b.setStyle(s.substring(0, i) + "-fx-background-color: " + color + ";" + s.substring(end + 1));
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Participants
    // ─────────────────────────────────────────────────────────────

    public void addParticipant(String username) {
        runOnFx(() -> {
            if (username == null || username.isBlank()) return;
            leftParticipants.remove(username);
            activeParticipants.add(username);
            if (videoMode && !username.equals(myUsername)) {
                ensureRemoteVideoView(username);
            }
            refreshParticipants();
        });
    }

    public void removeParticipant(String username) {
        runOnFx(() -> {
            if (username == null || username.isBlank()) return;

            leftParticipants.add(username);
            synchronized (activeParticipants) {
                activeParticipants.remove(username);
            }

            stopWaveAnimation(username);

            ImageView iv = remoteViews.remove(username);
            if (iv != null) {
                iv.setImage(null);
                iv.setVisible(false);
            }
            videoTiles.remove(username);
            audioTiles.remove(username);

            refreshParticipants();
        });
    }

    private void stopWaveAnimation(String username) {
        Timeline tl = waveTimelines.remove(username);
        if (tl != null) tl.stop();
    }

    private void stopAllWaveAnimations() {
        for (Timeline tl : waveTimelines.values()) tl.stop();
        waveTimelines.clear();
    }

    /** Rafraîchit la grille et la liste des participants (audio + vidéo). */
    public void refreshParticipants() {
        runOnFx(() -> {
            updateStatusText();
            if (videoMode) refreshVideoGrid();
            else refreshAudioGrid();
        });
    }

    private void runOnFx(Runnable r) {
        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
    }

    private List<String> copyActiveUsers() {
        List<String> users = new ArrayList<>();
        synchronized (activeParticipants) {
            for (String u : activeParticipants) {
                if (!leftParticipants.contains(u)) users.add(u);
            }
        }
        return users;
    }

    private void updateStatusText() {
        List<String> names = copyActiveUsers();
        int n = names.size();
        if (n == 0) {
            statusLbl.setText("Aucun participant");
            return;
        }
        List<String> display = new ArrayList<>();
        for (String u : names) {
            display.add(u.equals(myUsername) ? u + " (vous)" : u);
        }
        statusLbl.setText(n + " participant" + (n > 1 ? "s" : "") + " : " + String.join(", ", display));
    }

    // ─────────────────────────────────────────────────────────────
    //  Grille vidéo WhatsApp
    // ─────────────────────────────────────────────────────────────

    private void refreshVideoGrid() {
        if (videoGrid == null) return;

        videoGrid.getChildren().clear();
        videoGrid.getRowConstraints().clear();
        videoGrid.getColumnConstraints().clear();

        List<String> users = copyActiveUsers();
        int n = users.size();
        if (n == 0) return;

        int cols = gridCols(n);
        int rows = (int) Math.ceil((double) n / cols);

        for (int c = 0; c < cols; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / cols);
            cc.setHgrow(Priority.ALWAYS);
            cc.setFillWidth(true);
            videoGrid.getColumnConstraints().add(cc);
        }
        for (int r = 0; r < rows; r++) {
            RowConstraints rc = new RowConstraints();
            rc.setPercentHeight(100.0 / rows);
            rc.setVgrow(Priority.ALWAYS);
            rc.setFillHeight(true);
            videoGrid.getRowConstraints().add(rc);
        }

        for (int i = 0; i < n; i++) {
            String user = users.get(i);
            StackPane tile = getOrCreateVideoTile(user);
            GridPane.setConstraints(tile, i % cols, i / cols);
            GridPane.setMargin(tile, new Insets(2));
            videoGrid.add(tile, i % cols, i / cols);
        }
    }

    /** Colonnes selon le nombre de participants (style WhatsApp). */
    private static int gridCols(int n) {
        if (n <= 1) return 1;
        if (n == 2) return 2;
        if (n <= 4) return 2;
        if (n <= 6) return 3;
        if (n <= 9) return 3;
        return 4;
    }

    private StackPane getOrCreateVideoTile(String username) {
        if (videoTiles.containsKey(username)) return videoTiles.get(username);

        boolean isMe = username.equals(myUsername);
        StackPane tile = new StackPane();
        tile.setUserData(username);
        tile.setStyle("-fx-background-color: " + BG_TILE + "; -fx-background-radius: 8;"
                + "-fx-border-color: " + TILE_BORDER + "; -fx-border-radius: 8; -fx-border-width: 1;");
        tile.setMinSize(120, 90);
        tile.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        String initial = username.isEmpty() ? "?" : username.substring(0, 1).toUpperCase();
        Label avatarPh = new Label(initial);
        avatarPh.setStyle("-fx-background-color: #5E35B1; -fx-text-fill: white; -fx-font-weight: bold;"
                + "-fx-font-size: 36px; -fx-min-width: 80px; -fx-min-height: 80px; -fx-max-width: 80px;"
                + "-fx-max-height: 80px; -fx-background-radius: 40; -fx-alignment: center;");

        ImageView iv;
        if (isMe) {
            iv = localVideoView;
            iv.setPreserveRatio(true);
            iv.fitWidthProperty().bind(tile.widthProperty().subtract(8));
            iv.fitHeightProperty().bind(tile.heightProperty().subtract(28));
        } else {
            iv = ensureRemoteVideoView(username);
            iv.fitWidthProperty().unbind();
            iv.fitHeightProperty().unbind();
            iv.setPreserveRatio(true);
            iv.fitWidthProperty().bind(tile.widthProperty().subtract(8));
            iv.fitHeightProperty().bind(tile.heightProperty().subtract(28));
            iv.setSmooth(true);
            iv.setVisible(true);
            iv.imageProperty().addListener((obs, o, img) ->
                    avatarPh.setVisible(img == null || img.isError()));
        }

        Label nameLbl = new Label(username + (isMe ? " (vous)" : ""));
        nameLbl.setStyle("-fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 4 10;"
                + "-fx-background-color: rgba(0,0,0,0.55); -fx-background-radius: 6;");
        StackPane.setAlignment(nameLbl, Pos.BOTTOM_LEFT);
        StackPane.setMargin(nameLbl, new Insets(0, 0, 8, 8));

        tile.getChildren().addAll(avatarPh, iv, nameLbl);
        videoTiles.put(username, tile);
        return tile;
    }

    // ─────────────────────────────────────────────────────────────
    //  Grille audio
    // ─────────────────────────────────────────────────────────────

    private void refreshAudioGrid() {
        if (audioGrid == null) return;

        audioGrid.getChildren().clear();
        audioGrid.getRowConstraints().clear();
        audioGrid.getColumnConstraints().clear();

        List<String> users = copyActiveUsers();
        int n = users.size();

        if (emptyStateLbl != null) {
            emptyStateLbl.setVisible(n <= 1);
        }
        if (n == 0) {
            purgeStaleAudioTiles(Collections.emptySet());
            return;
        }

        purgeStaleAudioTiles(new HashSet<>(users));

        int cols = gridCols(n);
        int rows = (int) Math.ceil((double) n / cols);

        for (int c = 0; c < cols; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / cols);
            cc.setHgrow(Priority.ALWAYS);
            cc.setFillWidth(true);
            audioGrid.getColumnConstraints().add(cc);
        }
        for (int r = 0; r < rows; r++) {
            RowConstraints rc = new RowConstraints();
            rc.setPercentHeight(100.0 / rows);
            rc.setVgrow(Priority.ALWAYS);
            rc.setFillHeight(true);
            audioGrid.getRowConstraints().add(rc);
        }

        for (int i = 0; i < n; i++) {
            String user = users.get(i);
            StackPane tile = audioTiles.get(user);
            if (tile == null) {
                tile = buildWhatsAppAudioTile(user);
                audioTiles.put(user, tile);
            }
            GridPane.setConstraints(tile, i % cols, i / cols);
            GridPane.setMargin(tile, new Insets(6));
            audioGrid.add(tile, i % cols, i / cols);
        }
    }

    /** Supprime tuiles et animations des participants qui ne sont plus dans l'appel. */
    private void purgeStaleAudioTiles(Set<String> activeUsers) {
        List<String> stale = new ArrayList<>();
        for (String u : audioTiles.keySet()) {
            if (!activeUsers.contains(u)) stale.add(u);
        }
        for (String u : stale) {
            audioTiles.remove(u);
            stopWaveAnimation(u);
        }
    }

    private StackPane buildWhatsAppAudioTile(String username) {
        boolean isMe = username.equals(myUsername);
        String accent = WA_ACCENT[Math.abs(username.hashCode()) % WA_ACCENT.length];
        String initial = username.isEmpty() ? "?" : username.substring(0, 1).toUpperCase();

        StackPane tile = new StackPane();
        tile.setMinSize(160, 185);
        tile.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        tile.setStyle("-fx-background-color: " + WA_TILE_BG + ";"
                + "-fx-background-radius: 14;"
                + "-fx-border-color: " + accent + ";"
                + "-fx-border-width: 3;"
                + "-fx-border-radius: 14;");

        VBox body = new VBox(12);
        body.setAlignment(Pos.CENTER);
        body.setPadding(new Insets(24, 12, 18, 12));

        Label av = new Label(initial);
        av.setMinSize(88, 88);
        av.setMaxSize(88, 88);
        av.setAlignment(Pos.CENTER);
        av.setStyle("-fx-background-color: " + (isMe ? GREEN_ACCENT : "#3A4A54") + ";"
                + "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 34px;"
                + "-fx-background-radius: 44;");

        Label name = new Label(isMe ? "Vous" : username);
        name.setStyle("-fx-text-fill: " + TXT_MAIN + "; -fx-font-size: 15px; -fx-font-weight: bold;");
        name.setWrapText(true);
        name.setMaxWidth(150);
        name.setAlignment(Pos.CENTER);

        HBox wave = buildWaveformBars(accent);
        waveTimelines.put(username, startWaveAnimation(wave));

        body.getChildren().addAll(av, name, wave);
        tile.getChildren().add(body);
        return tile;
    }

    private HBox buildWaveformBars(String accentColor) {
        HBox box = new HBox(4);
        box.setAlignment(Pos.CENTER);
        Region[] bars = new Region[5];
        for (int i = 0; i < 5; i++) {
            Region bar = new Region();
            bar.setMinWidth(5);
            bar.setMaxWidth(5);
            bar.setMinHeight(6);
            bar.setPrefHeight(10 + (i % 3) * 5);
            bar.setStyle("-fx-background-color: " + accentColor + "; -fx-background-radius: 2;");
            bars[i] = bar;
            box.getChildren().add(bar);
        }
        box.setUserData(bars);
        return box;
    }

    private Timeline startWaveAnimation(HBox waveBox) {
        Region[] bars = (Region[]) waveBox.getUserData();
        Timeline tl = new Timeline(new KeyFrame(Duration.millis(130), e -> {
            for (Region bar : bars) {
                bar.setPrefHeight(6 + Math.random() * 20);
            }
        }));
        tl.setCycleCount(Timeline.INDEFINITE);
        tl.play();
        return tl;
    }

    /**
     * Crée l'ImageView distant dès l'arrivée d'un participant (avant refreshVideoGrid),
     * pour que VideoPlayer puisse y afficher les frames UDP immédiatement.
     */
    private ImageView ensureRemoteVideoView(String username) {
        return remoteViews.computeIfAbsent(username, u -> {
            ImageView iv = new ImageView();
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            iv.setVisible(true);
            return iv;
        });
    }

    // ─────────────────────────────────────────────────────────────
    //  API publique
    // ─────────────────────────────────────────────────────────────

    /**
     * Retourne l'ImageView distant pour afficher le flux UDP de ce participant.
     */
    public ImageView getRemoteViewForUser(String username) {
        if (username == null || username.equals(myUsername)) return null;
        if (leftParticipants.contains(username)) return null;

        synchronized (activeParticipants) {
            if (leftParticipants.contains(username)) return null;
            if (!activeParticipants.contains(username)) {
                if (!videoMode) return null;
                activeParticipants.add(username);
                ensureRemoteVideoView(username);
                refreshParticipants();
            }
        }
        return leftParticipants.contains(username) ? null : ensureRemoteVideoView(username);
    }

    public ImageView getLocalVideoView() { return localVideoView; }

    public ImageView getRemoteVideoView() {
        for (String u : remoteViews.keySet()) {
            if (!leftParticipants.contains(u)) return remoteViews.get(u);
        }
        return new ImageView();
    }

    public void clearLocalVideo() {
        Platform.runLater(() -> {
            localVideoView.setImage(null);
            refreshVideoGrid();
        });
    }

    public void setStatus(String s) {
        Platform.runLater(() -> statusLbl.setText(s));
    }

    public void show() {
        Platform.runLater(() -> {
            stage.show();
            stage.toFront();
            refreshParticipants();
        });
    }

    public void close() {
        if (closing) return;
        closing = true;
        timerRunning = false;
        stopAllWaveAnimations();
        leftParticipants.addAll(activeParticipants);
        activeParticipants.clear();
        remoteViews.clear();
        videoTiles.clear();
        audioTiles.clear();
        Runnable shut = () -> {
            if (stage.isShowing()) stage.close();
        };
        if (Platform.isFxApplicationThread()) shut.run();
        else Platform.runLater(shut);
    }

    private void startTimer() {
        timerRunning = true;
        Thread t = new Thread(() -> {
            while (timerRunning) {
                try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
                secondsElapsed++;
                final int s = secondsElapsed;
                Platform.runLater(() ->
                        durationLabel.setText(String.format("%02d:%02d", s / 60, s % 60)));
            }
        }, "meeting-timer");
        t.setDaemon(true);
        t.start();
    }
}
