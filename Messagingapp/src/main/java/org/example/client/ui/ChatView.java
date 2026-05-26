package org.example.client.ui;

import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.example.client.controller.ChatController;
import org.example.client.network.ClientSocketManager;
import org.example.utils.DisplayNames;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * ChatView V5 — Interface fidèle à WhatsApp Desktop (thème sombre).
 *
 * Layout :
 * ┌──────┬────────────────┬─────────────────────────────────────────┐
 * │ Nav  │  Sidebar       │  Zone conversation                      │
 * │ 58px │  330px         │  flexible                               │
 * └──────┴────────────────┴─────────────────────────────────────────┘
 *
 * Nav    : avatar | 💬 | 👥 | 📞 | ─spacer─ | ⚙
 * Sidebar: header (titre + bouton créer) | barre recherche | liste items
 * Conv   : header contact | messages | barre saisie
 *
 * Modes :
 *   CHATS  → liste contacts (en ligne first)
 *   GROUPS → liste groupes
 *   CALLS  → historique appels (tous) avec icônes + rappel
 */
public class ChatView {

    // ── Couleurs WA ───────────────────────────────────────────────────────
    private static final String BG_DARK    = "#111B21";
    private static final String BG_PANEL   = "#202C33";
    private static final String BG_CHAT    = "#0B141A";
    private static final String BG_INPUT   = "#2A3942";
    private static final String BG_HOVER   = "#2A3942";
    private static final String BG_SELECTED= "#2A3942";
    private static final String BG_BUBBLE_ME  = "#005C4B";
    private static final String BG_BUBBLE_YOU = "#202C33";
    private static final String BORDER     = "#2A3942";
    private static final String TXT_WHITE  = "#E9EDEF";
    private static final String TXT_GRAY   = "#8696A0";
    private static final String TXT_GREEN  = "#00A884";
    private static final String GREEN      = "#00A884";
    private static final String GREEN_DARK = "#005C4B";
    private static final String ONLINE_DOT = "#25D366";
    private static final String MISSED_RED = "#EA0038";

    // ── Controller & Stage ───────────────────────────────────────────────
    private final ChatController controller;
    private final Stage          stage;

    // ── Modes ─────────────────────────────────────────────────────────────
    private enum Mode { CHATS, GROUPS, CALLS }
    private Mode mode = Mode.CHATS;

    // ── Données ──────────────────────────────────────────────────────────
    private final Set<String>            onlineUsers  = new TreeSet<>();
    private final Set<String>            contacts     = new TreeSet<>();
    private final Map<String, GroupInfo> groups       = new LinkedHashMap<>();
    /** Dernier message affiché sous le nom du contact. */
    private final Map<String, String>    lastMessagePreview = new LinkedHashMap<>();
    /** Horodatage du dernier échange (tri + affichage à droite). */
    private final Map<String, String>    lastMessageTime    = new HashMap<>();
    /** Tri discussions : mis à jour seulement sur envoi/réception/appel (pas à l'ouverture). */
    private final Map<String, String>    lastActivityTime   = new HashMap<>();
    /** Nombre de messages non lus par contact. */
    private final Map<String, Integer>   unreadCount        = new HashMap<>();
    /** Aperçu / activité des groupes (comme les discussions). */
    private final Map<String, String>    lastGroupPreview   = new HashMap<>();
    private final Map<String, String>    lastGroupTime      = new HashMap<>();
    private final Map<String, String>    lastGroupActivity  = new HashMap<>();
    private String                       convSearchQuery    = "";

    // ── Historiques messages ──────────────────────────────────────────────
    private final Map<String, VBox>                 chatPanes    = new HashMap<>();
    private final Map<String, VBox>                 grpPanes     = new HashMap<>();
    private final Map<String, List<HistoryMsg>>     msgHistory   = new HashMap<>();
    private final Map<String, List<HistoryCallItem>>callHistory  = new HashMap<>();
    // Fichiers historiques (privé) : stockés avec timestamp réel pour tri chronologique
    private final Map<String, List<FileHistoryItem>> fileHistory  = new HashMap<>();
    // Historique groupes : messages texte + fichiers (tri chronologique unifié)
    private final Map<String, List<HistoryMsg>>          grpMsgHistory  = new HashMap<>();
    private final Map<String, List<GroupFileHistoryItem>> grpFileHistory = new HashMap<>();

    private static class FileHistoryItem {
        final String   timestamp;
        final String   fileName;
        final String   fileType;
        final byte[]   data;
        final boolean  fromMe;
        FileHistoryItem(String ts, String fn, String ft, byte[] d, boolean me) {
            timestamp=ts; fileName=fn; fileType=ft; data=d; fromMe=me;
        }
    }

    private static class GroupFileHistoryItem {
        final String timestamp, sender, fileName, fileType;
        final byte[] data;
        GroupFileHistoryItem(String ts, String sender, String fn, String ft, byte[] d) {
            this.timestamp = ts; this.sender = sender; this.fileName = fn;
            this.fileType = ft; this.data = d;
        }
    }

    // ── Historique appels global (pour vue appels) ────────────────────────
    private final List<CallLogEntry> allCallLog = new ArrayList<>();

    // ── Sélection ─────────────────────────────────────────────────────────
    private String selUser  = null;
    private String selGroup = null;

    // ── Widgets principaux ────────────────────────────────────────────────
    private HBox    root;
    private Label   connectionBanner;
    private ListView<Object> listView = new ListView<>();  // String (contact/group) ou CallLogEntry

    // Header conversation
    private HBox   convHeader;
    private Label  hdrAv, hdrName, hdrSub;
    private Button btnAudio, btnVideo;
    private Button btnGrpAudio, btnGrpVideo, btnGrpAdd, btnGrpRemove;

    // Zone chat
    private ScrollPane chatScroll;
    private HBox   inputBar;
    private TextField inputField;

    // Nav icons
    private Label navChats, navGroups, navCalls;

    public ChatView(ChatController ctrl, Stage stage) {
        this.controller = ctrl;
        this.stage      = stage;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  BUILD & SHOW
    // ══════════════════════════════════════════════════════════════════════

    public void show() {
        stage.setTitle("WhatsApp — " + displayName(controller.getUsername()));
        root = new HBox(0);
        root.setStyle("-fx-background-color:" + BG_DARK + ";");

        VBox       nav   = buildNav();
        VBox       side  = buildSidebar();
        BorderPane conv  = buildConvPanel();
        HBox.setHgrow(conv, Priority.ALWAYS);

        root.getChildren().addAll(nav, side, conv);

        connectionBanner = new Label();
        connectionBanner.setMaxWidth(Double.MAX_VALUE);
        connectionBanner.setAlignment(Pos.CENTER);
        connectionBanner.setStyle("-fx-background-color:#B45309;-fx-text-fill:white;" +
                "-fx-font-size:13px;-fx-padding:8 16;");
        connectionBanner.setVisible(false);

        VBox shell = new VBox(root);
        shell.getChildren().add(0, connectionBanner);
        VBox.setVgrow(root, Priority.ALWAYS);

        stage.setScene(new Scene(shell, 1180, 700));
        stage.setMinWidth(820); stage.setMinHeight(520);
        stage.show();
        controller.loadAllCallHistory();
        Platform.runLater(this::showWelcome);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  NAV BAR (colonne extrême gauche, 58px)
    // ══════════════════════════════════════════════════════════════════════

    private VBox buildNav() {
        VBox nav = new VBox(0);
        nav.setStyle("-fx-background-color:" + BG_PANEL + ";" +
                     "-fx-min-width:58px;-fx-max-width:58px;" +
                     "-fx-border-color:" + BORDER + ";-fx-border-width:0 1 0 0;");
        nav.setAlignment(Pos.TOP_CENTER);
        nav.setPadding(new Insets(12, 0, 12, 0));

        // Avatar propre avec indicateur en ligne
        Label myAv = new Label(initial(displayName(controller.getUsername())));
        myAv.setStyle(avStyle(GREEN, 40));
        myAv.setTooltip(new Tooltip(displayName(controller.getUsername()) + " — En ligne"));
        StackPane myAvWrap = new StackPane(myAv);
        Circle myOnlineDot = new Circle(5, Color.web(ONLINE_DOT));
        StackPane.setAlignment(myOnlineDot, Pos.BOTTOM_RIGHT);
        myOnlineDot.setTranslateX(2);
        myOnlineDot.setTranslateY(2);
        myAvWrap.getChildren().add(myOnlineDot);

        navChats  = navIcon("💬", "Discussions");
        navGroups = navIcon("👥", "Groupes");
        navCalls  = navIcon("📞", "Appels");

        navChats .setOnMouseClicked(e -> setMode(Mode.CHATS));
        navGroups.setOnMouseClicked(e -> setMode(Mode.GROUPS));
        navCalls .setOnMouseClicked(e -> setMode(Mode.CALLS));

        Region spacer = new Region(); VBox.setVgrow(spacer, Priority.ALWAYS);
        Label  profile = navIcon("👤", "Mon profil");
        profile.setOnMouseClicked(e -> showProfileDialog());

        nav.getChildren().addAll(myAvWrap,
                hpad(8), navChats,
                hpad(4), navGroups,
                hpad(4), navCalls,
                spacer, profile);

        selectNav(navChats);
        return nav;
    }

    private void setMode(Mode m) {
        mode    = m;
        selUser = null; selGroup = null;
        listView.getSelectionModel().clearSelection();
        selectNav(m == Mode.CHATS ? navChats : m == Mode.GROUPS ? navGroups : navCalls);

        if (m == Mode.CALLS) {
            controller.loadAllCallHistory(); // demander au serveur
        }

        refreshSidebar();
        showWelcome();
    }

    private void selectNav(Label active) {
        for (Label l : List.of(navChats, navGroups, navCalls)) {
            boolean sel = l == active;
            l.setStyle("-fx-font-size:20px;-fx-cursor:hand;-fx-padding:12 8;" +
                       "-fx-background-radius:10;-fx-text-fill:" + (sel ? TXT_WHITE : TXT_GRAY) + ";" +
                       (sel ? "-fx-background-color:#374151;" : ""));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SIDEBAR (330px)
    // ══════════════════════════════════════════════════════════════════════

    private VBox buildSidebar() {
        VBox side = new VBox(0);
        side.setStyle("-fx-background-color:" + BG_DARK + ";" +
                      "-fx-border-color:" + BORDER + ";-fx-border-width:0 1 0 0;" +
                      "-fx-min-width:330px;-fx-max-width:330px;");

        // ── Header ──────────────────────────────────────────────────────
        HBox hdr = new HBox(10);
        hdr.setStyle("-fx-background-color:" + BG_PANEL + ";-fx-padding:13 16;");
        hdr.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Discussions");
        title.setId("sideTitle");
        title.setStyle("-fx-text-fill:" + TXT_WHITE + ";-fx-font-size:18px;-fx-font-weight:bold;");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        Button newBtn = mkWaNewGroupBtn();
        newBtn.setOnAction(e -> {
            if (mode == Mode.GROUPS) showCreateGroupDialog();
            else if (mode == Mode.CHATS) {
                setMode(Mode.GROUPS);
                showCreateGroupDialog();
            }
        });
        newBtn.setTooltip(new Tooltip("Nouveau groupe"));

        hdr.getChildren().addAll(title, sp, newBtn);

        // ── Barre de recherche ───────────────────────────────────────────
        HBox searchRow = new HBox(8);
        searchRow.setStyle("-fx-background-color:" + BG_DARK + ";-fx-padding:8 10;");
        searchRow.setAlignment(Pos.CENTER);

        Label searchIco = new Label("🔍");
        searchIco.setStyle("-fx-font-size:14px;-fx-text-fill:" + TXT_GRAY + ";");

        TextField sf = new TextField();
        sf.setPromptText("Rechercher ou démarrer une discussion");
        sf.setStyle("-fx-background-color:" + BG_PANEL + ";-fx-background-radius:8;" +
                    "-fx-text-fill:" + TXT_WHITE + ";-fx-prompt-text-fill:" + TXT_GRAY + ";" +
                    "-fx-padding:7 12;-fx-font-size:13px;-fx-border-color:transparent;");
        sf.textProperty().addListener((obs, o, v) -> filterSidebar(v));
        HBox.setHgrow(sf, Priority.ALWAYS);
        searchRow.getChildren().addAll(searchIco, sf);

        // ── Liste ────────────────────────────────────────────────────────
        listView.setStyle("-fx-background-color:" + BG_DARK + ";-fx-border-color:transparent;" +
                          "-fx-background-insets:0;");
        listView.setCellFactory(lv -> new ItemCell());
        listView.getSelectionModel().selectedItemProperty()
                .addListener((obs, o, v) -> { if (v != null) onItemSelected(v); });
        VBox.setVgrow(listView, Priority.ALWAYS);

        side.getChildren().addAll(hdr, searchRow, listView);
        return side;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CONVERSATION PANEL (droite)
    // ══════════════════════════════════════════════════════════════════════

    private BorderPane buildConvPanel() {
        BorderPane bp = new BorderPane();
        bp.setStyle("-fx-background-color:" + BG_CHAT + ";");

        // ── Header (caché jusqu'à sélection) ────────────────────────────
        convHeader = new HBox(12);
        convHeader.setStyle("-fx-background-color:" + BG_PANEL + ";-fx-padding:10 16;" +
                            "-fx-border-color:transparent transparent" + BORDER + " transparent;" +
                            "-fx-border-width:0 0 1 0;");
        convHeader.setAlignment(Pos.CENTER_LEFT);
        convHeader.setVisible(false);

        hdrAv  = new Label("?"); hdrAv.setStyle(avStyle(GREEN, 40));
        hdrName= new Label(""); hdrName.setStyle("-fx-text-fill:" + TXT_WHITE + ";-fx-font-size:16px;-fx-font-weight:bold;");
        hdrSub = new Label(""); hdrSub.setStyle("-fx-text-fill:" + TXT_GRAY + ";-fx-font-size:12px;");
        VBox nameBox = new VBox(2, hdrName, hdrSub);
        HBox.setHgrow(nameBox, Priority.ALWAYS);

        Label srch = mkTxtBtn("🔍");
        srch.setOnMouseClicked(e -> toggleSearchInConv());
        btnAudio    = mkCircleBtn("📞", GREEN,  36);
        btnVideo    = mkCircleBtn("🎥", GREEN,  36);

        btnGrpAudio = mkCircleBtn("🎙", GREEN, 36); btnGrpAudio.setTooltip(new Tooltip("Réunion vocale"));
        btnGrpVideo = mkCircleBtn("📹", GREEN, 36); btnGrpVideo.setTooltip(new Tooltip("Réunion vidéo"));
        btnGrpAdd   = mkHdrIconBtn("user-add"); btnGrpAdd.setTooltip(new Tooltip("Ajouter un membre"));
        btnGrpRemove = mkHdrIconBtn("user-remove"); btnGrpRemove.setTooltip(new Tooltip("Retirer un membre"));
        btnGrpRemove.setOnAction(e -> showRemoveMemberDialog());

        btnAudio.setOnAction(e -> { if (selUser != null) controller.startAudioCall(selUser); });
        btnVideo.setOnAction(e -> { if (selUser != null) controller.startVideoCall(selUser); });
        btnGrpAudio.setOnAction(e -> { if (selGroup!=null) { var g=groups.get(selGroup); controller.startGroupAudioMeeting(selGroup, g!=null?g.name:""); }});
        btnGrpVideo.setOnAction(e -> { if (selGroup!=null) { var g=groups.get(selGroup); controller.startGroupVideoMeeting(selGroup, g!=null?g.name:""); }});
        btnGrpAdd.setOnAction(e -> { GroupInfo gAdmin=groups.get(selGroup); if(gAdmin!=null&&controller.getUsername().equals(gAdmin.admin)) showAddMemberDialog(); else new Alert(Alert.AlertType.WARNING,"Seul l'administrateur peut ajouter des membres.").showAndWait(); });

        hideHdrBtns();

        convSearchBar = new HBox(8);
        convSearchBar.setStyle("-fx-background-color:" + BG_PANEL + ";-fx-padding:6 14;");
        convSearchBar.setAlignment(Pos.CENTER_LEFT);
        convSearchBar.setVisible(false);
        convSearchBar.setManaged(false);
        Label searchIcoConv = new Label("🔍");
        searchIcoConv.setStyle("-fx-text-fill:" + TXT_GRAY + ";");
        TextField convSearchField = new TextField();
        convSearchField.setPromptText("Rechercher un message…");
        convSearchField.setStyle("-fx-background-color:" + BG_INPUT + ";-fx-text-fill:" + TXT_WHITE +
                ";-fx-prompt-text-fill:" + TXT_GRAY + ";-fx-background-radius:8;-fx-border-color:transparent;");
        convSearchField.textProperty().addListener((obs, o, v) -> {
            convSearchQuery = v != null ? v.trim().toLowerCase() : "";
            if (selUser != null) rebuildPrivPane(selUser);
            else if (selGroup != null) rebuildGrpPane(selGroup);
        });
        HBox.setHgrow(convSearchField, Priority.ALWAYS);
        convSearchBar.getChildren().addAll(searchIcoConv, convSearchField);

        VBox topBox = new VBox(convHeader, convSearchBar);
        convHeader.getChildren().addAll(hdrAv, nameBox, srch,
                btnAudio, btnVideo,
                btnGrpAudio, btnGrpVideo, btnGrpAdd, btnGrpRemove);
        bp.setTop(topBox);

        // ── Zone messages ─────────────────────────────────────────────
        chatScroll = new ScrollPane();
        chatScroll.setFitToWidth(true);
        chatScroll.setFitToHeight(false); // false pour que le contenu scroll correctement
        chatScroll.setStyle("-fx-background-color:" + BG_CHAT + ";-fx-background:" + BG_CHAT + ";" +
                            "-fx-border-color:transparent;");
        // Forcer le fond du viewport
        chatScroll.skinProperty().addListener((obs, o, n) -> {
            if (n != null) {
                javafx.scene.Node vp = chatScroll.lookup(".viewport");
                if (vp != null) vp.setStyle("-fx-background-color:" + BG_CHAT + ";");
            }
        });
        chatScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        chatScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        bp.setCenter(chatScroll);

        // ── Barre de saisie (cachée) ──────────────────────────────────
        inputBar = buildInputBar();
        inputBar.setVisible(false);
        bp.setBottom(inputBar);

        return bp;
    }

    private HBox buildInputBar() {
        HBox bar = new HBox(10);
        bar.setStyle("-fx-background-color:" + BG_PANEL + ";-fx-padding:8 14;");
        bar.setAlignment(Pos.CENTER);

        Label attach = mkTxtBtn("📎");
        attach.setOnMouseClicked(e -> pickFile());

        inputField = new TextField();
        inputField.setPromptText("Écrire un message");
        inputField.setStyle("-fx-background-color:" + BG_INPUT + ";-fx-background-radius:24;" +
                "-fx-text-fill:" + TXT_WHITE + ";-fx-prompt-text-fill:" + TXT_GRAY + ";" +
                "-fx-padding:10 16;-fx-font-size:14px;-fx-border-color:transparent;");
        inputField.setOnAction(e -> sendMsg());
        HBox.setHgrow(inputField, Priority.ALWAYS);

        Button send = mkCircleBtn("➤", GREEN, 42);
        send.setOnAction(e -> sendMsg());

        bar.getChildren().addAll(attach, inputField, send);
        return bar;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  WELCOME SCREEN
    // ══════════════════════════════════════════════════════════════════════

    private void showWelcome() {
        convHeader.setVisible(false);
        inputBar.setVisible(false);

        // Utiliser StackPane pour couvrir tout l'espace disponible
        StackPane wrapper = new StackPane();
        wrapper.setStyle("-fx-background-color:" + BG_CHAT + ";");
        wrapper.setMinWidth(600);
        wrapper.setMinHeight(600);

        VBox w = new VBox(0);
        w.setStyle("-fx-background-color:" + BG_CHAT + ";");
        w.setFillWidth(true);

        // Section haute
        VBox top = new VBox(20);
        top.setAlignment(Pos.CENTER);
        top.setStyle("-fx-background-color:" + BG_CHAT + ";-fx-padding:60 40 40 40;");
        VBox.setVgrow(top, Priority.ALWAYS);

        Label ico   = new Label("💬");
        ico.setStyle("-fx-font-size:72px;");

        Label title = new Label(mode == Mode.CALLS ? "Historique des appels" :
                                mode == Mode.GROUPS ? "Vos groupes" : "WhatsApp pour PC");
        title.setStyle("-fx-text-fill:" + TXT_WHITE + ";-fx-font-size:26px;-fx-font-weight:bold;");

        Label sub = new Label(mode == Mode.CALLS
                ? "Vos appels vocaux et vidéo privés et de groupe."
                : mode == Mode.GROUPS
                ? "Créez et gérez vos groupes de discussion."
                : "Envoyez des messages, passez des appels et partagez des fichiers.");
        sub.setStyle("-fx-text-fill:" + TXT_GRAY + ";-fx-font-size:13px;");
        sub.setWrapText(true);
        sub.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        sub.setMaxWidth(440);

        top.getChildren().addAll(ico, title, sub);

        // Séparateur
        Region sepLine = new Region();
        sepLine.setStyle("-fx-background-color:" + BORDER + ";");
        sepLine.setMinHeight(1); sepLine.setMaxHeight(1);

        // Section basse (hint)
        Label hint = new Label(mode == Mode.CHATS
                ? "Sélectionnez un contact pour commencer à discuter."
                : mode == Mode.GROUPS
                ? "Sélectionnez un groupe pour ouvrir la discussion."
                : "Sélectionnez un appel dans la liste pour voir les détails.");
        hint.setStyle("-fx-text-fill:" + TXT_GRAY + ";-fx-font-size:13px;-fx-padding:20;");
        hint.setWrapText(true);
        hint.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        hint.setMaxWidth(440);

        VBox bot = new VBox(0, hint);
        bot.setAlignment(Pos.TOP_CENTER);
        bot.setStyle("-fx-background-color:" + BG_CHAT + ";");

        w.getChildren().addAll(top, sepLine, bot);
        wrapper.getChildren().add(w);
        chatScroll.setContent(wrapper);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SÉLECTION ITEM
    // ══════════════════════════════════════════════════════════════════════

    private void onItemSelected(Object item) {
        if (item instanceof String id) {
            if (mode == Mode.CHATS)  selectUser(id);
            else if (mode == Mode.GROUPS) selectGroup(id);
        } else if (item instanceof CallLogEntry e) {
            if (e.peer != null && e.peer.startsWith("👥")) return;
            mode = Mode.CHATS;
            selectNav(navChats);
            selectUser(e.peer);
            listView.getSelectionModel().clearSelection();
        }
    }

    // Peers dont l'historique a déjà été chargé cette session
    private final java.util.Set<String> histLoadedPeers = new java.util.HashSet<>();

    private void selectUser(String user) {
        selUser = user; selGroup = null;
        clearUnread(user);
        hdrAv.setGraphic(null);
        hdrAv.setText(initial(displayName(user)));
        hdrAv.setStyle(avStyle(avColor(user), 40));
        hdrName.setText(displayName(user));
        updateConvHeaderOnlineStatus(user);
        showConvUI(true);
        updateHdrBtns();
        VBox pane = getPrivPane(user);
        chatScroll.setContent(pane);
        // Charger UNE SEULE FOIS par session : évite de dupliquer les fichiers
        if (histLoadedPeers.add(user)) {
            controller.loadHistory(user);
        }
        scrollBottom();
    }

    // Groupes dont l'historique a déjà été chargé cette session
    private final java.util.Set<String> histLoadedGroups = new java.util.HashSet<>();

    private void selectGroup(String gid) {
        selGroup = gid; selUser = null;
        GroupInfo g = groups.get(gid);
        String name = g != null ? g.name : gid;
        hdrAv.setGraphic(groupAvatar(40));
        hdrAv.setText("");
        hdrAv.setStyle("-fx-background-color:transparent;-fx-padding:0;");
        hdrName.setText(name);
        updateGroupHeaderMembers(g);
        showConvUI(true);
        updateHdrBtns();
        chatScroll.setContent(getGrpPane(gid));
        // Charger UNE SEULE FOIS par session
        if (histLoadedGroups.add(gid)) {
            controller.loadGroupHistory(gid);
        }
        scrollBottom();
    }

    private void showConvUI(boolean show) {
        convHeader.setVisible(show);
        inputBar.setVisible(show);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  REFRESH SIDEBAR
    // ══════════════════════════════════════════════════════════════════════

    private void refreshSidebar() {
        Platform.runLater(() -> {
            // Titre
            Node t = root.lookup("#sideTitle");
            if (t instanceof Label lbl) lbl.setText(
                mode == Mode.CHATS ? "Discussions" :
                mode == Mode.GROUPS ? "Groupes" : "Appels");

            List<Object> items = new ArrayList<>();
            if (mode == Mode.CHATS) {
                List<String> allContacts = new ArrayList<>();
                contacts.forEach(allContacts::add);
                onlineUsers.forEach(u -> { if (!allContacts.contains(u)) allContacts.add(u); });
                allContacts.sort((a, b) -> {
                    long ta = contactSortEpoch(a);
                    long tb = contactSortEpoch(b);
                    if (ta != tb) return Long.compare(tb, ta);
                    int ua = unreadCount.getOrDefault(a, 0);
                    int ub = unreadCount.getOrDefault(b, 0);
                    if (ua != ub) return Integer.compare(ub, ua);
                    return a.compareToIgnoreCase(b);
                });
                items.addAll(allContacts);
            } else if (mode == Mode.GROUPS) {
                List<String> gids = new ArrayList<>(groups.keySet());
                gids.sort((a, b) -> Long.compare(groupSortEpoch(b), groupSortEpoch(a)));
                items.addAll(gids);
            } else { // CALLS
                if (!allCallLog.isEmpty()) {
                    items.addAll(allCallLog);
                } else {
                    // Pas encore chargé — afficher les contacts pour appel rapide
                    onlineUsers.forEach(u -> { if (!items.contains(u)) items.add(u); });
                    contacts.stream().filter(cc -> !items.contains(cc)).forEach(items::add);
                }
            }

            Object sel = listView.getSelectionModel().getSelectedItem();
            listView.getItems().setAll(items);
            if (sel != null && items.contains(sel)) listView.getSelectionModel().select(sel);
        });
    }

    private void filterSidebar(String q) {
        Platform.runLater(() -> {
            if (q == null || q.isBlank()) { refreshSidebar(); return; }
            String lower = q.toLowerCase();
            List<Object> filtered = listView.getItems().stream().filter(item -> {
                if (item instanceof String s) {
                    if (mode == Mode.GROUPS) {
                        GroupInfo g = groups.get(s);
                        return g != null && g.name.toLowerCase().contains(lower);
                    }
                    return s.toLowerCase().contains(lower);
                }
                if (item instanceof CallLogEntry e) return e.peer.toLowerCase().contains(lower);
                return false;
            }).toList();
            listView.getItems().setAll(filtered);
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ENVOI
    // ══════════════════════════════════════════════════════════════════════

    private void sendMsg() {
        if (mode == Mode.CHATS && selUser != null)  sendPrivate();
        else if (mode == Mode.GROUPS && selGroup != null) sendGroup();
    }

    private void sendPrivate() {
        String text = inputField.getText().trim(); if (text.isEmpty()) return;
        controller.sendMessage(selUser, text);
        String ts = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        HistoryMsg sent = new HistoryMsg(controller.getUsername(), selUser, ts, text);
        msgHistory.computeIfAbsent(selUser, k -> new ArrayList<>()).add(sent);
        setLastMessage(selUser, "Vous: " + text, false);
        VBox pane = getPrivPane(selUser);
        Platform.runLater(() -> { pane.getChildren().add(bubble(sent, true, nowTime())); scrollBottom(); });
        inputField.clear();
        updatePendingBanner();
        if (controller.getPendingCount() == 0) controller.loadHistory(selUser);
    }

    private void sendGroup() {
        String text = inputField.getText().trim(); if (text.isEmpty()) return;
        controller.sendGroupMessage(selGroup, text);
        String ts = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        grpMsgHistory.computeIfAbsent(selGroup, k -> new ArrayList<>())
                .add(new HistoryMsg(controller.getUsername(), selGroup, ts, text));
        bumpGroupActivity(selGroup, "Vous: " + text);
        Platform.runLater(() -> { rebuildGrpPane(selGroup); });
        inputField.clear();
        updatePendingBanner();
        if (controller.getPendingCount() == 0) controller.loadGroupHistory(selGroup);
    }

    private void updatePendingBanner() {
        int n = controller.getPendingCount();
        if (n > 0) {
            setConnectionStatus(false, n + " message(s) en attente — envoi à la reconnexion…");
        }
    }

    private void pickFile() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images", "*.png","*.jpg","*.jpeg","*.gif"),
                new FileChooser.ExtensionFilter("PDF", "*.pdf"),
                new FileChooser.ExtensionFilter("Tous", "*.*"));
        File file = fc.showOpenDialog(stage); if (file == null) return;
        new Thread(() -> {
            try {
                byte[] data = java.nio.file.Files.readAllBytes(file.toPath());
                String name = file.getName(), type = fileType(name);
                if (selUser != null) {
                    final String u = selUser;
                    controller.sendFile(u, name, type, data);
                    // Stocker dans fileHistory pour cohérence avec rebuildPrivPane
                    String ts = java.time.LocalDateTime.now()
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    fileHistory.computeIfAbsent(u, k -> new java.util.ArrayList<>())
                              .add(new FileHistoryItem(ts, name, type, data, true));
                    Platform.runLater(() -> rebuildPrivPane(u));
                } else if (selGroup != null) {
                    final String g = selGroup;
                    controller.sendGroupFile(g, name, type, data);
                    String ts = java.time.LocalDateTime.now()
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    grpFileHistory.computeIfAbsent(g, k -> new ArrayList<>())
                            .add(new GroupFileHistoryItem(ts, controller.getUsername(), name, type, data));
                    Platform.runLater(() -> rebuildGrpPane(g));
                }
            } catch (IOException ex) { Platform.runLater(() -> alert(ex.getMessage())); }
        }, "file-picker").start();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  API PUBLIQUE — CONTACTS
    // ══════════════════════════════════════════════════════════════════════

    public void addContact(String u) {
        if (u==null||u.isBlank()||u.equals(controller.getUsername())) return;
        contacts.add(u); Platform.runLater(() -> { if (mode==Mode.CHATS) refreshSidebar(); });
    }

    public void updateSavedContacts(String txt) {
        contacts.clear();
        if (txt == null) {
            Platform.runLater(() -> { if (mode == Mode.CHATS) refreshSidebar(); });
            return;
        }
        for (String line : txt.split("\\R")) {
            if (line.isBlank()) continue;
            String[] p = line.split("\\t", -1);
            String c = p[0].trim();
            if (c.isBlank() || c.equals(controller.getUsername())) continue;
            contacts.add(c);
            if (p.length >= 2) {
                String preview = ClientSocketManager.decodeContactPreview(p[1]);
                if (!preview.isBlank()) {
                    lastMessagePreview.put(c, truncatePreview(preview));
                }
            }
            if (p.length >= 3 && !p[2].isBlank()) {
                String ts = normalizeTimestamp(p[2]);
                lastMessageTime.put(c, ts);
                lastActivityTime.put(c, ts);
            }
            if (p.length >= 4) {
                try {
                    int n = Integer.parseInt(p[3].trim());
                    if (n > 0) unreadCount.put(c, n);
                    else unreadCount.remove(c);
                } catch (NumberFormatException ignored) {}
            }
        }
        Platform.runLater(() -> { if (mode == Mode.CHATS) refreshSidebar(); });
    }

    public void updateUserList(String csv) {
        onlineUsers.clear();
        if (csv!=null) for (String u : csv.split(",")) { String x=u.trim(); if (!x.isBlank()&&!x.equals(controller.getUsername())) onlineUsers.add(x); }
        Platform.runLater(() -> {
            if (mode == Mode.CHATS) refreshSidebar();
            if (selUser != null) updateConvHeaderOnlineStatus(selUser);
        });
    }

    public void appendMessage(String peer, String text, boolean fromMe) {
        if (fromMe) return; // déjà affiché dans sendPrivate()
        if (peer==null) return;
        String ts = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        msgHistory.computeIfAbsent(peer, k -> new ArrayList<>())
                .add(new HistoryMsg(peer, controller.getUsername(), ts, text));
        VBox pane = getPrivPane(peer);
        pane.getChildren().add(bubble(text, false, nowTime()));
        boolean open = peer.equals(selUser);
        setLastMessage(peer, text, !open);
        if (open) {
            chatScroll.setContent(pane);
            scrollBottom();
        }
    }

    public void appendFile(String peer, String fn, String ft, byte[] data, boolean fromMe) {
        appendFile(peer, fn, ft, data, fromMe, null);
    }

    public void appendFile(String peer, String fn, String ft, byte[] data, boolean fromMe, String uploadedAt) {
        if (peer == null) return;
        // Timestamp: utiliser uploadedAt (historique BDD) ou maintenant (live)
        String ts = (uploadedAt != null && !uploadedAt.isBlank())
                ? normalizeTimestamp(uploadedAt)
                : java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        List<FileHistoryItem> files = fileHistory.computeIfAbsent(peer, k -> new java.util.ArrayList<>());
        boolean duplicate = files.stream()
                .anyMatch(f -> f.fileName.equals(fn) && f.timestamp.equals(ts) && f.fromMe == fromMe);
        if (!duplicate) {
            files.add(new FileHistoryItem(ts, fn, ft, data, fromMe));
        }
        String preview = fromMe
                ? (ft != null && ft.startsWith("image") ? "Vous: 📷 Photo" : "Vous: 📎 " + fn)
                : (ft != null && ft.startsWith("image") ? "📷 Photo" : "📎 " + fn);
        boolean historyLoad = uploadedAt != null && !uploadedAt.isBlank();
        if (historyLoad) {
            syncLastActivityFromPeer(peer);
        } else {
            setLastMessage(peer, preview, !fromMe && !peer.equals(selUser));
        }
        rebuildPrivPane(peer);
    }

    private void setLastMessage(String peer, String preview, boolean incrementUnread) {
        if (peer == null || preview == null) return;
        String p = preview.trim();
        if (p.length() > 52) p = p.substring(0, 49) + "...";
        lastMessagePreview.put(peer, p);
        String now = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        lastMessageTime.put(peer, now);
        lastActivityTime.put(peer, now);
        if (incrementUnread) {
            unreadCount.merge(peer, 1, Integer::sum);
        }
        Platform.runLater(() -> { if (mode == Mode.CHATS) refreshSidebar(); });
    }

    /** Met à jour l'aperçu / tri sans utiliser l'heure actuelle (ouverture d'historique). */
    private void syncLastActivityFromPeer(String peer) {
        String bestTs = null;
        String bestPreview = null;

        for (HistoryMsg m : msgHistory.getOrDefault(peer, List.of())) {
            if (m.time == null || m.time.isBlank()) continue;
            if (bestTs == null || chronosKey(m.time) >= chronosKey(bestTs)) {
                bestTs = m.time;
                String me = controller.getUsername();
                bestPreview = m.sender.equals(me) ? "Vous: " + m.text : m.text;
            }
        }
        for (FileHistoryItem f : fileHistory.getOrDefault(peer, List.of())) {
            if (f.timestamp == null || f.timestamp.isBlank()) continue;
            if (bestTs == null || chronosKey(f.timestamp) >= chronosKey(bestTs)) {
                bestTs = f.timestamp;
                bestPreview = f.fromMe
                        ? (f.fileType != null && f.fileType.startsWith("image") ? "Vous: 📷 Photo" : "Vous: 📎 " + f.fileName)
                        : (f.fileType != null && f.fileType.startsWith("image") ? "📷 Photo" : "📎 " + f.fileName);
            }
        }

        if (bestTs == null) return;
        String ts = normalizeTimestamp(bestTs);
        long best = chronosKey(ts);
        long current = chronosKey(lastActivityTime.get(peer));
        if (best >= current) {
            lastActivityTime.put(peer, ts);
            lastMessageTime.put(peer, ts);
            if (bestPreview != null) {
                lastMessagePreview.put(peer, truncatePreview(bestPreview));
            }
            Platform.runLater(() -> { if (mode == Mode.CHATS) refreshSidebar(); });
        }
    }

    private void clearUnread(String peer) {
        if (peer == null) return;
        if (unreadCount.remove(peer) != null) {
            Platform.runLater(() -> { if (mode == Mode.CHATS) refreshSidebar(); });
        }
    }

    private void syncLastMessageFromHistory(String peer) {
        syncLastActivityFromPeer(peer);
    }

    private String truncatePreview(String preview) {
        if (preview == null) return "";
        String p = preview.trim();
        if (p.length() > 52) p = p.substring(0, 49) + "...";
        return p;
    }

    private String contactSubtitle(String contactId) {
        return lastMessagePreview.getOrDefault(contactId, "");
    }

    private int getUnreadCount(String contactId) {
        return unreadCount.getOrDefault(contactId, 0);
    }

    private long contactSortEpoch(String contactId) {
        return chronosKey(lastActivityTime.get(contactId));
    }

    private long groupSortEpoch(String groupId) {
        return chronosKey(lastGroupActivity.get(groupId));
    }

    private void bumpGroupActivity(String gid, String preview) {
        if (gid == null) return;
        String now = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        if (preview != null && !preview.isBlank()) {
            lastGroupPreview.put(gid, truncatePreview(preview));
        }
        lastGroupTime.put(gid, now);
        lastGroupActivity.put(gid, now);
        Platform.runLater(() -> { if (mode == Mode.GROUPS) refreshSidebar(); });
    }

    public void recordCallActivity(String peer, String callType) {
        if (peer == null || peer.isBlank()) return;
        boolean vid = "VIDEO".equalsIgnoreCase(callType);
        setLastMessage(peer, vid ? "Appel vidéo" : "Appel vocal", false);
    }

    public void onMessageDeleted(String payload) {
        if (payload == null) return;
        Platform.runLater(() -> {
            if (payload.startsWith("PRIVATE|")) {
                String[] p = payload.split("\\|", 3);
                if (p.length < 3) return;
                int id; try { id = Integer.parseInt(p[1]); } catch (Exception e) { return; }
                String peer = p[2];
                List<HistoryMsg> msgs = msgHistory.get(peer);
                if (msgs != null) {
                    msgs.removeIf(m -> m.id == id);
                    rebuildPrivPane(peer);
                }
            } else if (payload.startsWith("GROUP|")) {
                String[] p = payload.split("\\|", 3);
                if (p.length < 3) return;
                int id; try { id = Integer.parseInt(p[1]); } catch (Exception e) { return; }
                String gid = p[2];
                List<HistoryMsg> msgs = grpMsgHistory.get(gid);
                if (msgs != null) {
                    msgs.removeIf(m -> m.id == id);
                    rebuildGrpPane(gid);
                }
            }
        });
    }

    private String groupSubtitle(String gid) {
        return lastGroupPreview.getOrDefault(gid, "");
    }

    private String groupTimeLabel(String gid) {
        return shortDate(lastGroupTime.get(gid));
    }

    private void updateGroupHeaderMembers(GroupInfo g) {
        if (g == null) { hdrSub.setText(""); return; }
        List<String> ordered = new ArrayList<>(g.members);
        ordered.sort((a, b) -> {
            if (a.equals(g.admin)) return -1;
            if (b.equals(g.admin)) return 1;
            return a.compareToIgnoreCase(b);
        });
        hdrSub.setText("Membres : " + ordered.stream().map(this::displayName).reduce((a, b) -> a + ", " + b).orElse(""));
    }

    private String contactTimeLabel(String contactId) {
        return shortDate(lastMessageTime.get(contactId));
    }

    private boolean isUserOnline(String user) {
        if (user == null || user.isBlank()) return false;
        if (user.equals(controller.getUsername())) return true;
        return onlineUsers.contains(user);
    }

    private void updateConvHeaderOnlineStatus(String user) {
        hdrSub.setText(isUserOnline(user) ? "En ligne" : "Hors ligne");
    }

    /** Évite les doublons après envoi hors ligne puis reconnexion. */
    private static boolean matchesHistoryMessage(HistoryMsg server, HistoryMsg live, String me) {
        if (!server.sender.equals(live.sender) || !server.text.equals(live.text)) return false;
        if (server.time.equals(live.time)) return true;
        return live.id <= 0 && live.sender.equals(me);
    }

    private Label buildUnreadBadge(int count) {
        String txt = count > 99 ? "99+" : String.valueOf(count);
        Label badge = new Label(txt);
        badge.setMinSize(22, 22);
        badge.setAlignment(Pos.CENTER);
        badge.setStyle("-fx-background-color:" + TXT_GREEN + ";-fx-text-fill:white;"
                + "-fx-font-size:11px;-fx-font-weight:bold;-fx-background-radius:11;"
                + "-fx-padding:2 6;");
        return badge;
    }

    public void setHeaderStatus(String s) { Platform.runLater(() -> hdrSub.setText(s)); }

    public void setConversationHistory(String peer, String content) {
        if (peer==null||peer.isBlank()) return;
        // Fusionner avec les messages live déjà en mémoire (envoyés avant la réponse serveur)
        List<HistoryMsg> serverMsgs = parseMsgs(content);
        List<HistoryMsg> liveMsgs   = msgHistory.getOrDefault(peer, List.of());
        if (!liveMsgs.isEmpty()) {
            List<HistoryMsg> merged = new ArrayList<>(serverMsgs);
            String me = controller.getUsername();
            for (HistoryMsg live : liveMsgs) {
                boolean known = serverMsgs.stream().anyMatch(s -> matchesHistoryMessage(s, live, me));
                if (!known) merged.add(live);
            }
            msgHistory.put(peer, merged);
        } else {
            msgHistory.put(peer, serverMsgs);
        }
        syncLastMessageFromHistory(peer);
        rebuildPrivPane(peer);
        Platform.runLater(() -> { if (mode == Mode.CHATS) refreshSidebar(); });
    }

    public void setCallHistory(String peer, String content) {
        if (peer==null||peer.isBlank()) return;
        callHistory.put(peer, parseCalls(content));
        rebuildPrivPane(peer);
    }

    /** Reçoit TOUS les appels pour la vue 📞. */
    public void setAllCallHistory(String content) {
        allCallLog.clear();
        if (content != null) {
            for (String line : content.split("\\R")) {
                if (line.isBlank()) continue;
                String[] p = line.split("\\t", -1);
                if (p.length < 7) continue;
                int dur = 0; try { dur = Integer.parseInt(p[6]); } catch(Exception ignored) {}
                boolean fromMe = controller.getUsername().equals(p[1]);
                allCallLog.add(new CallLogEntry(p[0], p[3], p[4], p[5], dur, fromMe));
            }
        }
        Platform.runLater(() -> { if (mode == Mode.CALLS) refreshSidebar(); });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  API PUBLIQUE — GROUPES
    // ══════════════════════════════════════════════════════════════════════

    public void onGroupCreated(String id, String name, List<String> members) {
        Platform.runLater(() -> {
            GroupInfo info = new GroupInfo(id, name, controller.getUsername());
            members.forEach(m -> { if (!info.members.contains(m)) info.members.add(m); });
            groups.put(id, info);
            if (mode==Mode.GROUPS) refreshSidebar();
        });
    }

    public void onGroupAdded(String id, String name, List<String> members) {
        Platform.runLater(() -> {
            GroupInfo info = groups.computeIfAbsent(id, k -> new GroupInfo(id, name, "?"));
            info.name = name; info.members.clear();
            members.stream().filter(m->!m.isBlank()).forEach(m -> { if(!info.members.contains(m)) info.members.add(m); });
            if (mode==Mode.GROUPS) refreshSidebar();
        });
    }

    public void onGroupList(String raw) {
        Platform.runLater(() -> {
            for (String line : raw.split("\n")) {
                if (line.isBlank()) continue;
                String meta = line;
                String previewPart = "";
                String timePart = "";
                int pipe = line.indexOf('|');
                if (pipe >= 0) {
                    meta = line.substring(0, pipe);
                    String rest = line.substring(pipe + 1);
                    int pipe2 = rest.indexOf('|');
                    if (pipe2 >= 0) {
                        previewPart = rest.substring(0, pipe2);
                        timePart = rest.substring(pipe2 + 1);
                    }
                }
                String[] p = meta.split(":", 4);
                if (p.length < 4) continue;
                GroupInfo info = groups.computeIfAbsent(p[0], k -> new GroupInfo(p[0], p[1], p[2]));
                info.name = p[1];
                info.admin = p[2];
                info.members.clear();
                for (String m : p[3].split(",")) {
                    if (!m.isBlank() && !info.members.contains(m)) info.members.add(m);
                }
                if (!previewPart.isBlank()) {
                    lastGroupPreview.put(p[0], truncatePreview(ClientSocketManager.decodeContactPreview(previewPart)));
                }
                if (!timePart.isBlank()) {
                    String ts = normalizeTimestamp(timePart);
                    lastGroupTime.put(p[0], ts);
                    lastGroupActivity.put(p[0], ts);
                }
            }
            if (mode == Mode.GROUPS) refreshSidebar();
        });
    }

    public void appendGroupMessage(String gid, String sender, String text, boolean fromMe) {
        if (fromMe) return; // déjà affiché dans sendGroup()
        if (gid == null) return;
        String ts = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        grpMsgHistory.computeIfAbsent(gid, k -> new ArrayList<>())
                .add(new HistoryMsg(sender, gid, ts, text));
        String me = controller.getUsername();
        bumpGroupActivity(gid, sender.equals(me) ? "Vous: " + text : sender + ": " + text);
        Platform.runLater(() -> rebuildGrpPane(gid));
    }

    public void appendGroupFile(String gid, String sender, String fn, String ft, byte[] data, boolean fromMe) {
        appendGroupFile(gid, sender, fn, ft, data, fromMe, null);
    }

    public void appendGroupFile(String gid, String sender, String fn, String ft,
                                byte[] data, boolean fromMe, String uploadedAt) {
        if (gid == null) return;
        String ts = (uploadedAt != null && !uploadedAt.isBlank())
                ? normalizeTimestamp(uploadedAt)
                : java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        List<GroupFileHistoryItem> files = grpFileHistory.computeIfAbsent(gid, k -> new ArrayList<>());
        // Compléter un placeholder créé par setGroupHistory (données binaires pas encore reçues)
        boolean merged = false;
        for (int i = 0; i < files.size(); i++) {
            GroupFileHistoryItem f = files.get(i);
            if (f.sender.equals(sender) && f.fileName.equals(fn) && f.data == null) {
                files.set(i, new GroupFileHistoryItem(
                        uploadedAt != null ? normalizeTimestamp(uploadedAt) : f.timestamp,
                        sender, fn, ft, data));
                merged = true;
                break;
            }
        }
        if (!merged) {
            boolean duplicate = files.stream().anyMatch(f ->
                    f.fileName.equals(fn) && f.timestamp.equals(ts) && f.sender.equals(sender) && f.data != null);
            if (!duplicate) {
                files.add(new GroupFileHistoryItem(ts, sender, fn, ft, data));
            }
        }
        String me = controller.getUsername();
        String fp = fromMe ? "Vous: 📎 " + fn : sender + ": 📎 " + fn;
        if (ft != null && ft.startsWith("image")) {
            fp = fromMe ? "Vous: 📷 Photo" : sender + ": 📷 Photo";
        }
        boolean historyLoad = uploadedAt != null && !uploadedAt.isBlank();
        if (historyLoad) {
            syncLastGroupActivityFromHistory(gid);
        } else {
            bumpGroupActivity(gid, fp);
        }
        Platform.runLater(() -> rebuildGrpPane(gid));
    }

    private final java.util.Map<String,Integer> grpHistLoaded  = new java.util.HashMap<>();
    private final java.util.Map<String,Integer> privHistLoaded = new java.util.HashMap<>();

    public void setGroupHistory(String gid, String content) {
        final String groupId;
        if (gid == null || gid.isBlank()) {
            groupId = extractGroupIdFromHistory(content);
            if (groupId == null || groupId.isBlank()) return;
        } else {
            groupId = gid;
        }
        List<HistoryMsg> serverMsgs = new ArrayList<>();
        List<GroupFileHistoryItem> serverFiles = new ArrayList<>();
        if (content != null && !content.isBlank()) {
            for (String line : content.split("\\R")) {
                if (line.isBlank()) continue;
                String[] p = line.split("\\t", -1);
                if (p.length < 4) continue;
                boolean hasId = p.length >= 6 && p[0].matches("\\d+");
                String ts = normalizeTimestamp(hasId ? p[3] : p[2]);
                if (hasId && p.length >= 6 && "FILE".equals(p[5])) {
                    String fn = decode(p[4]);
                    serverFiles.add(new GroupFileHistoryItem(ts, p[1], fn, fileType(fn), null));
                } else if (!hasId && p.length >= 5 && "FILE".equals(p[4])) {
                    String fn = decode(p[3]);
                    serverFiles.add(new GroupFileHistoryItem(ts, p[0], fn, fileType(fn), null));
                } else if (hasId) {
                    serverMsgs.add(new HistoryMsg(Integer.parseInt(p[0]), p[1], groupId, ts, decode(p[4])));
                } else {
                    serverMsgs.add(new HistoryMsg(p[0], groupId, ts, decode(p[3])));
                }
            }
        }
        List<HistoryMsg> liveMsgs = grpMsgHistory.getOrDefault(groupId, List.of());
        if (!liveMsgs.isEmpty()) {
            List<HistoryMsg> merged = new ArrayList<>(serverMsgs);
            String me = controller.getUsername();
            for (HistoryMsg live : liveMsgs) {
                boolean known = serverMsgs.stream().anyMatch(s -> matchesHistoryMessage(s, live, me));
                if (!known) merged.add(live);
            }
            grpMsgHistory.put(groupId, merged);
        } else {
            grpMsgHistory.put(groupId, serverMsgs);
        }
        // Fusionner fichiers serveur + fichiers live déjà reçus cette session
        List<GroupFileHistoryItem> liveFiles = grpFileHistory.getOrDefault(groupId, List.of());
        List<GroupFileHistoryItem> mergedFiles = new ArrayList<>(serverFiles);
        for (GroupFileHistoryItem ph : serverFiles) {
            liveFiles.stream()
                    .filter(lf -> lf.data != null && lf.sender.equals(ph.sender) && lf.fileName.equals(ph.fileName))
                    .findFirst()
                    .ifPresent(lf -> {
                        int idx = mergedFiles.indexOf(ph);
                        if (idx >= 0) mergedFiles.set(idx, lf);
                    });
        }
        for (GroupFileHistoryItem lf : liveFiles) {
            boolean known = mergedFiles.stream()
                    .anyMatch(sf -> sf.sender.equals(lf.sender) && sf.fileName.equals(lf.fileName));
            if (!known) mergedFiles.add(lf);
        }
        grpFileHistory.put(groupId, mergedFiles);
        syncLastGroupPreviewFromHistory(groupId);
        Platform.runLater(() -> rebuildGrpPane(groupId));
    }

    private void syncLastGroupPreviewFromHistory(String gid) {
        syncLastGroupActivityFromHistory(gid);
    }

    private void syncLastGroupActivityFromHistory(String gid) {
        String bestTs = null;
        String bestPreview = null;
        String me = controller.getUsername();

        for (HistoryMsg m : grpMsgHistory.getOrDefault(gid, List.of())) {
            if (m.time == null || m.time.isBlank()) continue;
            if (bestTs == null || chronosKey(m.time) >= chronosKey(bestTs)) {
                bestTs = m.time;
                bestPreview = "SYSTEM".equals(m.sender) ? m.text
                        : (m.sender.equals(me) ? "Vous: " + m.text : m.sender + ": " + m.text);
            }
        }
        for (GroupFileHistoryItem f : grpFileHistory.getOrDefault(gid, List.of())) {
            if (f.timestamp == null || f.timestamp.isBlank()) continue;
            if (bestTs == null || chronosKey(f.timestamp) >= chronosKey(bestTs)) {
                bestTs = f.timestamp;
                bestPreview = f.sender.equals(me) ? "Vous: 📎 " + f.fileName : f.sender + ": 📎 " + f.fileName;
                if (f.fileType != null && f.fileType.startsWith("image")) {
                    bestPreview = f.sender.equals(me) ? "Vous: 📷 Photo" : f.sender + ": 📷 Photo";
                }
            }
        }

        if (bestTs == null) return;
        String ts = normalizeTimestamp(bestTs);
        long best = chronosKey(ts);
        long current = chronosKey(lastGroupActivity.get(gid));
        if (best >= current) {
            lastGroupActivity.put(gid, ts);
            lastGroupTime.put(gid, ts);
            if (bestPreview != null) {
                lastGroupPreview.put(gid, truncatePreview(bestPreview));
            }
            Platform.runLater(() -> { if (mode == Mode.GROUPS) refreshSidebar(); });
        }
    }

    /** Extrait groupId depuis la première ligne de l'historique (colonne 2). */
    private String extractGroupIdFromHistory(String content) {
        if (content == null || content.isBlank()) return null;
        for (String line : content.split("\\R")) {
            if (line.isBlank()) continue;
            String[] p = line.split("\\t", -1);
            if (p.length >= 2 && !p[1].isBlank()) return p[1];
        }
        return null;
    }

    public void onMemberJoined(String gid, String user, List<String> allMembers) {
        Platform.runLater(() -> {
            GroupInfo info = groups.get(gid);
            if (info != null) {
                if (allMembers!=null&&!allMembers.isEmpty()) { info.members.clear(); info.members.addAll(allMembers); }
                else if (!info.members.contains(user)) info.members.add(user);
                if (gid.equals(selGroup)) updateGroupHeaderMembers(info);
                if (mode==Mode.GROUPS) refreshSidebar();
            }
        });
    }

    public void onMemberLeft(String gid, String user) {
        Platform.runLater(() -> {
            GroupInfo info = groups.get(gid);
            if (info != null) {
                info.members.remove(user);
                if (gid.equals(selGroup)) updateGroupHeaderMembers(info);
                if (mode==Mode.GROUPS) refreshSidebar();
                String ts = java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                grpMsgHistory.computeIfAbsent(gid, k -> new ArrayList<>())
                        .add(new HistoryMsg("SYSTEM", gid, ts, "🚫 " + user + " a été retiré du groupe."));
                rebuildGrpPane(gid);
            }
        });
    }

    /** L'utilisateur courant a été retiré du groupe. */
    public void onGroupRemoved(String gid, String groupName) {
        Platform.runLater(() -> {
            groups.remove(gid);
            grpPanes.remove(gid);
            grpMsgHistory.remove(gid);
            grpFileHistory.remove(gid);
            grpLiveNodes.remove(gid);
            if (gid.equals(selGroup)) {
                selGroup = null;
                showWelcome();
            }
            if (mode == Mode.GROUPS) refreshSidebar();
            // Notification
            javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
            a.setTitle("Groupe"); a.setContentText("Vous avez ete retire du groupe \"" + groupName + "\"."); a.showAndWait();
        });
    }

    public Set<String> getAllContacts() { Set<String> a=new TreeSet<>(contacts); a.addAll(onlineUsers); return a; }

    // ══════════════════════════════════════════════════════════════════════
    //  PANES & REBUILD
    // ══════════════════════════════════════════════════════════════════════

    private VBox getPrivPane(String user) {
        return chatPanes.computeIfAbsent(user, k -> {
            VBox v = new VBox(4); v.setStyle("-fx-background-color:" + BG_CHAT + ";-fx-padding:12 16;"); return v;
        });
    }

    private VBox getGrpPane(String gid) {
        return grpPanes.computeIfAbsent(gid, k -> {
            VBox v = new VBox(4); v.setStyle("-fx-background-color:" + BG_CHAT + ";-fx-padding:12 16;"); return v;
        });
    }

    // Map pour sauvegarder les nœuds live (fichiers envoyés/reçus cette session) par peer
    // Chaque entrée est un couple (isoTimestamp, node) pour respecter l'ordre chronologique
    private final Map<String, List<LiveNode>> privLiveNodes = new HashMap<>();
    private final Map<String, List<LiveNode>> grpLiveNodes  = new HashMap<>();

    private static class LiveNode {
        final String timestamp; // ISO datetime ou HH:mm
        final javafx.scene.Node node;
        LiveNode(String ts, javafx.scene.Node n) { timestamp = ts; node = n; }
    }

    private void rebuildGrpPane(String gid) {
        List<HistoryEntry> entries = new ArrayList<>();
        for (HistoryMsg m : grpMsgHistory.getOrDefault(gid, List.of())) {
            if (!matchesConvSearch(m.text)) continue;
            if ("SYSTEM".equals(m.sender)) {
                entries.add(new HistoryEntry(m.time, sysMsgBubble(m.text)));
            } else {
                boolean me = controller.getUsername().equals(m.sender);
                entries.add(new HistoryEntry(m.time, grpBubble(m, me, shortTime(m.time))));
            }
        }
        for (GroupFileHistoryItem fi : grpFileHistory.getOrDefault(gid, List.of())) {
            if (fi.data == null) continue; // en attente du GroupFileMessage binaire
            boolean me = controller.getUsername().equals(fi.sender);
            entries.add(new HistoryEntry(fi.timestamp,
                    fileBubble(fi.fileName, fi.fileType, fi.data, me, me ? null : fi.sender)));
        }
        entries.sort(Comparator.comparingLong(e -> chronosKey(e.time)));
        VBox pane = getGrpPane(gid);
        Platform.runLater(() -> {
            pane.getChildren().clear();
            GroupInfo g = groups.get(gid);
            if (g != null) pane.getChildren().add(buildGroupMembersBar(g));
            String lastDate = "";
            for (HistoryEntry e : entries) {
                String d = dateLabel(e.time);
                if (!d.equals(lastDate)) { pane.getChildren().add(dateSep(d)); lastDate = d; }
                pane.getChildren().add(e.node);
            }
            if (gid.equals(selGroup)) { chatScroll.setContent(pane); scrollBottom(); }
        });
    }

    private Node buildGroupMembersBar(GroupInfo g) {
        FlowPane fp = new FlowPane(6, 6);
        fp.setPadding(new Insets(0, 0, 10, 0));
        List<String> ordered = new ArrayList<>(g.members);
        ordered.sort((a, b) -> {
            if (a.equals(g.admin)) return -1;
            if (b.equals(g.admin)) return 1;
            return a.compareToIgnoreCase(b);
        });
        for (String m : ordered) {
            Label chip = new Label(displayName(m) + (m.equals(g.admin) ? " ★" : ""));
            chip.setStyle("-fx-background-color:" + BG_INPUT + ";-fx-text-fill:" + TXT_WHITE +
                    ";-fx-font-size:11px;-fx-padding:4 10;-fx-background-radius:12;");
            fp.getChildren().add(chip);
        }
        return fp;
    }

    private void rebuildPrivPane(String peer) {
        List<HistoryEntry> entries = new ArrayList<>();
        // Messages texte
        for (HistoryMsg m : msgHistory.getOrDefault(peer, List.of())) {
            if (!matchesConvSearch(m.text)) continue;
            boolean me = controller.getUsername().equals(m.sender);
            entries.add(new HistoryEntry(m.time, bubble(m, me, shortTime(m.time))));
        }
        // Appels
        for (HistoryCallItem c : callHistory.getOrDefault(peer, List.of())) {
            boolean me = controller.getUsername().equals(c.caller);
            entries.add(new HistoryEntry(c.startedAt, callBubble(c.type, c.status, me, c.dur)));
        }
        // Fichiers (historique + live) — triés avec leur vrai timestamp
        for (FileHistoryItem fi : fileHistory.getOrDefault(peer, List.of())) {
            entries.add(new HistoryEntry(fi.timestamp, fileBubble(fi.fileName, fi.fileType, fi.data, fi.fromMe, null)));
        }
        // Trier chronologiquement (comparaison numérique, pas lexicographique)
        entries.sort(Comparator.comparingLong(e -> chronosKey(e.time)));
        VBox pane = getPrivPane(peer);
        Platform.runLater(() -> {
            pane.getChildren().clear();
            String lastDate = "";
            for (HistoryEntry e : entries) {
                String d = dateLabel(e.time);
                if (!d.equals(lastDate)) { pane.getChildren().add(dateSep(d)); lastDate = d; }
                pane.getChildren().add(e.node);
            }
            if (peer.equals(selUser)) { chatScroll.setContent(pane); scrollBottom(); }
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  BULLES
    // ══════════════════════════════════════════════════════════════════════

    private Node bubble(String text, boolean me, String time) {
        HistoryMsg m = new HistoryMsg(controller.getUsername(), selUser, "", text);
        m.time = time;
        return bubble(m, me, time);
    }

    private Node bubble(HistoryMsg m, boolean me, String time) {
        Label msg = new Label(m.text);
        msg.setWrapText(true); msg.setMaxWidth(400);
        msg.setStyle("-fx-font-size:14px;-fx-text-fill:" + TXT_WHITE + ";");
        Label tl = new Label(time);
        tl.setStyle("-fx-font-size:11px;-fx-text-fill:" + TXT_GRAY + ";");
        HBox tr = new HBox(tl); tr.setAlignment(me ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        VBox bub = new VBox(3, msg, tr); bub.setMaxWidth(420);
        bub.setStyle((me ? "-fx-background-color:" + BG_BUBBLE_ME : "-fx-background-color:" + BG_BUBBLE_YOU) +
                ";-fx-background-radius:" + (me ? "8 8 0 8" : "8 8 8 0") + ";-fx-padding:8 12;");
        HBox row = new HBox(bub); row.setPadding(new Insets(2,0,2,0));
        row.setAlignment(me ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        if (me && m.id > 0 && selUser != null) {
            addDeleteOption(row, bub, me, () -> controller.deletePrivateMessage(selUser, m.id));
        }
        return row;
    }

    private Node grpBubble(HistoryMsg m, boolean me, String time) {
        VBox bub = new VBox(3); bub.setMaxWidth(420);
        if (!me) {
            Label sl = new Label(displayName(m.sender));
            sl.setStyle("-fx-font-size:11px;-fx-font-weight:bold;-fx-text-fill:" + GREEN + ";");
            bub.getChildren().add(sl);
        }
        Label msg = new Label(m.text); msg.setWrapText(true); msg.setMaxWidth(400);
        msg.setStyle("-fx-font-size:14px;-fx-text-fill:" + TXT_WHITE + ";");
        Label tl = new Label(time); tl.setStyle("-fx-font-size:11px;-fx-text-fill:" + TXT_GRAY + ";");
        HBox tr = new HBox(tl); tr.setAlignment(me ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        bub.getChildren().addAll(msg, tr);
        bub.setStyle((me ? "-fx-background-color:" + BG_BUBBLE_ME : "-fx-background-color:" + BG_BUBBLE_YOU) +
                ";-fx-background-radius:" + (me ? "8 8 0 8" : "8 8 8 0") + ";-fx-padding:8 12;");
        GroupInfo g = selGroup != null ? groups.get(selGroup) : null;
        boolean canDelete = m.id > 0 && selGroup != null
                && (me || (g != null && controller.getUsername().equals(g.admin)));
        HBox row = new HBox(bub); row.setPadding(new Insets(2,0,2,0));
        row.setAlignment(me ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        if (canDelete) {
            addDeleteOption(row, bub, me, () -> controller.deleteGroupMessage(selGroup, m.id));
        }
        return row;
    }

    private void addDeleteOption(HBox row, Node bub, boolean me, Runnable onDelete) {
        Runnable confirmDelete = () -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Supprimer ce message ?",
                    ButtonType.OK, ButtonType.CANCEL);
            confirm.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> onDelete.run());
        };
        ContextMenu menu = new ContextMenu();
        MenuItem delItem = new MenuItem("Supprimer");
        delItem.setOnAction(e -> confirmDelete.run());
        menu.getItems().add(delItem);
        bub.setOnContextMenuRequested(e -> menu.show(bub, e.getScreenX(), e.getScreenY()));

        Button delBtn = new Button("🗑");
        delBtn.setTooltip(new Tooltip("Supprimer"));
        delBtn.setStyle("-fx-background-color:transparent;-fx-text-fill:" + TXT_GRAY +
                ";-fx-font-size:14px;-fx-cursor:hand;-fx-padding:4;-fx-border-color:transparent;");
        delBtn.setOpacity(0);
        delBtn.setOnAction(e -> confirmDelete.run());
        row.setOnMouseEntered(e -> delBtn.setOpacity(1));
        row.setOnMouseExited(e -> delBtn.setOpacity(0));
        if (me) row.getChildren().add(0, delBtn);
        else row.getChildren().add(delBtn);
    }

    private Node callBubble(String type, String status, boolean fromMe, int dur) {
        boolean vid    = "VIDEO".equalsIgnoreCase(type);
        boolean missed = "MISSED".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status);
        String icon    = vid ? "📹" : "📞";
        String dir     = fromMe ? " ↗" : " ↙";
        String label   = switch (status == null ? "" : status.toUpperCase()) {
            case "ENDED"    -> (vid ? "Appel vidéo" : "Appel vocal") + " terminé";
            case "MISSED"   -> "Appel manqué";
            case "REJECTED" -> "Appel refusé";
            case "ACCEPTED" -> (vid ? "Appel vidéo" : "Appel vocal") + " accepté";
            default         -> (vid ? "Appel vidéo" : "Appel vocal");
        };
        String durText = dur > 0 ? "  " + (dur/60 > 0 ? dur/60 + "m " : "") + dur%60 + "s" : "";

        Label iconLbl = new Label(icon + dir);
        iconLbl.setStyle("-fx-font-size:16px;-fx-text-fill:" + (missed ? MISSED_RED : GREEN) + ";");
        Label txt = new Label(label + durText);
        txt.setStyle("-fx-font-size:13px;-fx-text-fill:" + TXT_WHITE + ";");
        Label tl = new Label(nowTime());
        tl.setStyle("-fx-font-size:11px;-fx-text-fill:" + TXT_GRAY + ";");

        HBox content = new HBox(8, iconLbl, txt); content.setAlignment(Pos.CENTER_LEFT);
        VBox bub = new VBox(4, content, tl);
        bub.setStyle("-fx-background-color:" + BG_BUBBLE_YOU + ";-fx-background-radius:8;-fx-padding:8 14;");
        bub.setMaxWidth(300);
        HBox row = new HBox(bub); row.setPadding(new Insets(3,0,3,0)); row.setAlignment(Pos.CENTER);
        return row;
    }

    private Node fileBubble(String fn, String ft, byte[] data, boolean me, String sender) {
        VBox cnt = new VBox(6);
        if (sender != null) {
            Label sl = new Label(displayName(sender));
            sl.setStyle("-fx-font-size:11px;-fx-font-weight:bold;-fx-text-fill:" + GREEN + ";");
            cnt.getChildren().add(sl);
        }
        if ("image".equals(ft) && data != null) {
            // Charger avec taille max 220px pour éviter OutOfMemoryError sur grandes images
            javafx.scene.image.Image img = new javafx.scene.image.Image(
                    new java.io.ByteArrayInputStream(data),
                    220, 0, true, true);  // width=220, height=auto, preserveRatio, smooth
            ImageView iv = new ImageView(img);
            iv.setFitWidth(220); iv.setPreserveRatio(true); cnt.getChildren().add(iv);
        } else {
            String ico = "pdf".equals(ft) ? "📄" : "video".equals(ft) ? "🎬" : "📎";
            Label fl = new Label(ico + " " + fn); fl.setStyle("-fx-font-size:13px;-fx-text-fill:" + TXT_WHITE + ";"); cnt.getChildren().add(fl);
        }
        Button sv = new Button("⬇ Enregistrer"); sv.setStyle("-fx-background-color:" + GREEN + ";-fx-text-fill:white;-fx-cursor:hand;-fx-font-size:11px;-fx-background-radius:6;-fx-border-color:transparent;"); sv.setOnAction(e -> saveFile(fn, data)); cnt.getChildren().add(sv);
        VBox bub = new VBox(cnt); bub.setMaxWidth(260);
        bub.setStyle("-fx-background-color:" + (me ? BG_BUBBLE_ME : BG_BUBBLE_YOU) + ";-fx-background-radius:8;-fx-padding:8 12;");
        HBox row = new HBox(bub); row.setPadding(new Insets(2,0,2,0)); row.setAlignment(me ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        return row;
    }

    /** Bulle système (retrait membre, etc.) centrée — style WhatsApp. */
    private Node sysMsgBubble(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-background-color:#182229;-fx-text-fill:#8696A0;-fx-font-size:12px;-fx-padding:4 14;-fx-background-radius:8;");
        l.setWrapText(true); l.setMaxWidth(400);
        HBox row = new HBox(l); row.setAlignment(Pos.CENTER); row.setPadding(new Insets(6,0,6,0)); return row;
    }

    private Node dateSep(String label) {
        Label l = new Label(label);
        l.setStyle("-fx-background-color:#182229;-fx-text-fill:" + TXT_GRAY + ";-fx-font-size:12px;-fx-padding:4 12;-fx-background-radius:8;");
        HBox row = new HBox(l); row.setAlignment(Pos.CENTER); row.setPadding(new Insets(6,0,6,0)); return row;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CELL RENDERER
    // ══════════════════════════════════════════════════════════════════════

    private class ItemCell extends ListCell<Object> {
        @Override protected void updateItem(Object item, boolean empty) {
            super.updateItem(item, empty);
            setStyle("-fx-background-color:transparent;-fx-padding:0;");
            if (empty || item == null) { setGraphic(null); return; }

            HBox row = new HBox(14);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(10, 14, 10, 14));
            boolean sel = isSelected();
            row.setStyle("-fx-background-color:" + (sel ? BG_SELECTED : "transparent") +
                         ";-fx-border-color:transparent transparent " + BORDER + " transparent;-fx-border-width:0 0 1 0;");

            if (item instanceof CallLogEntry e) {
                renderCallRow(row, e, sel);
            } else if (item instanceof String id) {
                if (mode == Mode.CALLS) {
                    renderQuickCallRow(row, id, sel);
                } else {
                    renderConvRow(row, id, sel);
                }
            }

            setGraphic(row);
        }

        private void renderConvRow(HBox row, String id, boolean sel) {
            String name, sub; String avCol;

            if (mode == Mode.GROUPS) {
                GroupInfo g = groups.get(id);
                name  = g != null ? g.name : id;
                sub   = groupSubtitle(id);
                avCol = "#128C7E";
            } else {
                name  = id;
                sub   = contactSubtitle(id);
                avCol = avColor(id);
            }

            Node avNode;
            if (mode == Mode.GROUPS) {
                avNode = groupAvatar(48);
            } else {
                Label av = new Label(initial(displayName(name)));
                av.setStyle(avStyle(avCol, 48));
                avNode = av;
            }

            int unreadN = mode == Mode.CHATS ? getUnreadCount(id) : 0;
            boolean unread = unreadN > 0;
            String nameStyle = "-fx-text-fill:" + TXT_WHITE + ";-fx-font-size:15px;"
                    + (unread ? "-fx-font-weight:bold;" : "");
            Label nameL = new Label(displayName(name));
            nameL.setStyle(nameStyle);

            VBox info;
            if (sub != null && !sub.isBlank()) {
                String subColor = unread ? TXT_GREEN : TXT_GRAY;
                Label subL = new Label(sub);
                subL.setStyle("-fx-font-size:12px;-fx-text-fill:" + subColor + ";");
                subL.setMaxWidth(180);
                info = new VBox(3, nameL, subL);
            } else {
                info = new VBox(3, nameL);
            }
            HBox.setHgrow(info, Priority.ALWAYS);

            VBox rightCol = new VBox(4);
            rightCol.setAlignment(Pos.TOP_RIGHT);
            String timeLbl = mode == Mode.CHATS ? contactTimeLabel(id)
                    : (mode == Mode.GROUPS ? groupTimeLabel(id) : "");
            if (timeLbl != null && !timeLbl.isBlank()) {
                Label timeL = new Label(timeLbl);
                String timeColor = unread ? TXT_GREEN : TXT_GRAY;
                timeL.setStyle("-fx-font-size:11px;-fx-text-fill:" + timeColor + ";");
                rightCol.getChildren().add(timeL);
            }
            if (unread) {
                rightCol.getChildren().add(buildUnreadBadge(unreadN));
            }
            row.getChildren().addAll(avNode, info, rightCol);
        }

        private void renderCallRow(HBox row, CallLogEntry e, boolean sel) {
            boolean vid    = "VIDEO".equalsIgnoreCase(e.type);
            boolean missed = "MISSED".equalsIgnoreCase(e.status) || "REJECTED".equalsIgnoreCase(e.status);

            Label av = new Label(initial(e.peer)); av.setStyle(avStyle(avColor(e.peer), 48));

            Label nameL = new Label(e.peer); nameL.setStyle("-fx-text-fill:" + TXT_WHITE + ";-fx-font-size:15px;-fx-font-weight:bold;");

            // Sous-titre : icône direction + type + durée
            String dirIcon = e.fromMe ? "↗ " : "↙ ";
            String typeStr = vid ? "Vidéo" : "Vocal";
            String durStr  = e.dur > 0 ? " · " + (e.dur/60>0?e.dur/60+"m ":"") + e.dur%60+"s" : "";
            Label sub = new Label(dirIcon + typeStr + durStr);
            sub.setStyle("-fx-font-size:12px;-fx-text-fill:" + (missed ? MISSED_RED : TXT_GRAY) + ";");

            // Date
            Label dateL = new Label(shortDate(e.date));
            dateL.setStyle("-fx-font-size:11px;-fx-text-fill:" + TXT_GRAY + ";");

            VBox info = new VBox(3, nameL, sub); HBox.setHgrow(info, Priority.ALWAYS);

            boolean isGroupCall = e.peer != null && e.peer.startsWith("👥");
            Button call = null;
            if (!isGroupCall) {
                call = mkCircleBtn(vid ? "📹" : "📞", GREEN, 34);
                call.setOnAction(ev -> {
                    if (vid) controller.startVideoCall(e.peer);
                    else     controller.startAudioCall(e.peer);
                });
            }

            if (call != null) row.getChildren().addAll(av, info, dateL, call);
            else row.getChildren().addAll(av, info, dateL);
        }

        private void renderQuickCallRow(HBox row, String peer, boolean sel) {
            // Affiché quand pas encore d'historique — permet appel rapide
            Label av = new Label(initial(peer)); av.setStyle(avStyle(avColor(peer), 48));
            Label nameL = new Label(peer); nameL.setStyle("-fx-text-fill:" + TXT_WHITE + ";-fx-font-size:15px;-fx-font-weight:bold;");
            Label sub = new Label("Appeler"); sub.setStyle("-fx-font-size:12px;-fx-text-fill:" + TXT_GRAY + ";");
            VBox info = new VBox(3, nameL, sub); HBox.setHgrow(info, Priority.ALWAYS);
            Button callBtn   = mkCircleBtn("📞", GREEN, 34);
            Button videoBtn2 = mkCircleBtn("📹", GREEN, 34);
            callBtn.setOnAction(e  -> controller.startAudioCall(peer));
            videoBtn2.setOnAction(e -> controller.startVideoCall(peer));
            row.getChildren().addAll(av, info, callBtn, videoBtn2);
        }
    } // end ItemCell

    // ══════════════════════════════════════════════════════════════════════
    //  DIALOGS GROUPE
    // ══════════════════════════════════════════════════════════════════════

    private void showCreateGroupDialog() {
        TextInputDialog d = new TextInputDialog();
        d.setTitle("Nouveau groupe"); d.setHeaderText("Créer un groupe"); d.setContentText("Nom du groupe :");
        d.showAndWait().filter(s -> !s.isBlank()).ifPresent(name -> showMemberPicker(name.trim(), new ArrayList<>(getAllContacts()), null));
    }

    private void showAddMemberDialog() {
        if (selGroup == null) return;
        GroupInfo g = groups.get(selGroup);
        List<String> avail = new ArrayList<>(getAllContacts());
        if (g != null) avail.removeAll(g.members);
        if (avail.isEmpty()) { alert("Tous vos contacts sont déjà membres."); return; }
        showMemberPicker(null, avail, selGroup);
    }

    private void showMemberPicker(String gName, List<String> list, String existingId) {
        showMemberPicker(gName, list, existingId, false);
    }

    private void showMemberPicker(String titleOrGroupName, List<String> list, String existingId, boolean removeMode) {
        if (!removeMode && list.isEmpty() && titleOrGroupName != null) {
            controller.createGroupNoMembers(titleOrGroupName);
            return;
        }
        Dialog<List<String>> d = new Dialog<>();
        d.setTitle(removeMode ? titleOrGroupName : (existingId == null ? "Nouveau groupe" : "Ajouter des membres"));
        d.setHeaderText(null);
        d.getDialogPane().setStyle("-fx-background-color:#1F2937;");
        ButtonType ok = new ButtonType(removeMode ? "Retirer" : "Confirmer", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);

        TextField srch = new TextField();
        srch.setPromptText("Rechercher...");
        srch.setStyle("-fx-background-color:#374151;-fx-text-fill:#E9EDEF;-fx-background-radius:8;" +
                "-fx-prompt-text-fill:#6B7280;-fx-padding:8 12;-fx-border-color:transparent;-fx-font-size:13px;");

        VBox cbBox = new VBox(0);
        List<CheckBox> boxes = new ArrayList<>();
        List<HBox> rowsList = new ArrayList<>();
        String[] pal = {"#E53935","#D81B60","#8E24AA","#1E88E5","#00897B","#43A047","#FB8C00"};

        for (String cc : list) {
            CheckBox chk = new CheckBox(); chk.setStyle("-fx-cursor:hand;");
            boxes.add(chk);
            String ini = cc.isEmpty() ? "?" : cc.substring(0,1).toUpperCase();
            String col = pal[Math.abs(cc.hashCode()) % pal.length];
            Label av = new Label(ini);
            av.setStyle("-fx-background-color:"+col+";-fx-text-fill:white;-fx-font-weight:bold;" +
                    "-fx-min-width:42px;-fx-min-height:42px;-fx-max-width:42px;-fx-max-height:42px;" +
                    "-fx-background-radius:21;-fx-alignment:center;-fx-font-size:16px;");
            Label nameL = new Label(displayName(cc));
            nameL.setStyle("-fx-text-fill:#E9EDEF;-fx-font-size:14px;-fx-font-weight:bold;");
            boolean online = isUserOnline(cc);
            Label subL = new Label(online ? "En ligne" : "Hors ligne");
            subL.setStyle("-fx-font-size:11px;-fx-text-fill:"+(online?"#25D366":"#8696A0")+";");
            VBox nfo = new VBox(2, nameL, subL); HBox.setHgrow(nfo, Priority.ALWAYS);
            HBox row = new HBox(14, av, nfo, chk);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(10, 16, 10, 16));
            String rowBase = "-fx-background-color:#1F2937;-fx-border-color:#2A3942;-fx-border-width:0 0 1 0;-fx-cursor:hand;";
            row.setStyle(rowBase);
            row.setOnMouseClicked(e -> chk.setSelected(!chk.isSelected()));
            row.setOnMouseEntered(e -> row.setStyle("-fx-background-color:#2A3942;-fx-border-color:#374151;-fx-border-width:0 0 1 0;-fx-cursor:hand;"));
            row.setOnMouseExited(e  -> row.setStyle(rowBase));
            rowsList.add(row); cbBox.getChildren().add(row);
        }
        srch.textProperty().addListener((obs,o,v) -> {
            cbBox.getChildren().clear();
            for (int i=0;i<list.size();i++) if (list.get(i).toLowerCase().contains(v.toLowerCase())) cbBox.getChildren().add(rowsList.get(i));
        });
        ScrollPane sp = new ScrollPane(cbBox); sp.setFitToWidth(true);
        sp.setPrefHeight(Math.min(list.size()*62+24, 400));
        sp.setStyle("-fx-background-color:#1F2937;-fx-background:#1F2937;");
        VBox wrap = new VBox(0, srch, sp); wrap.setStyle("-fx-background-color:#1F2937;");
        srch.setPadding(new Insets(10,14,10,14));
        d.getDialogPane().setContent(wrap);

        d.setResultConverter(btn -> {
            if (btn != ok) return null;
            List<String> sel = new ArrayList<>();
            for (int i=0; i<boxes.size(); i++) if (boxes.get(i).isSelected()) sel.add(list.get(i));
            return sel;
        });
        d.showAndWait().ifPresent(sel -> {
            if (sel.isEmpty()) return;
            if (removeMode && existingId != null) {
                controller.removeMember(existingId, sel.get(0));
            } else if (existingId == null) {
                controller.createGroup(titleOrGroupName, sel);
            } else {
                sel.forEach(m -> controller.addMember(existingId, m));
            }
        });
    }
    // ══════════════════════════════════════════════════════════════════════
    //  PROFIL & RETRAIT MEMBRE
    // ══════════════════════════════════════════════════════════════════════

    public void onAuthOk(String display) {
        Platform.runLater(() -> {
            stage.setTitle("WhatsApp — " + (display != null && !display.isBlank()
                    ? display : displayName(controller.getUsername())));
        });
    }

    public void setConnectionStatus(boolean connected, String message) {
        Platform.runLater(() -> {
            if (connectionBanner == null) return;
            if (message != null && !message.isBlank()) connectionBanner.setText(message);
            if (connected) {
                connectionBanner.setStyle("-fx-background-color:#15803D;-fx-text-fill:white;" +
                        "-fx-font-size:13px;-fx-padding:8 16;");
                connectionBanner.setVisible(true);
                new Thread(() -> {
                    try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                    Platform.runLater(() -> {
                        if (connectionBanner != null) connectionBanner.setVisible(false);
                    });
                }, "hide-reconnect-banner").start();
            } else {
                connectionBanner.setStyle("-fx-background-color:#B45309;-fx-text-fill:white;" +
                        "-fx-font-size:13px;-fx-padding:8 16;");
                connectionBanner.setVisible(true);
            }
        });
    }

    public void refreshAfterReconnect() {
        Platform.runLater(() -> {
            if (selUser != null) {
                updateConvHeaderOnlineStatus(selUser);
                controller.loadHistory(selUser);
            }
            if (selGroup != null) controller.loadGroupHistory(selGroup);
            refreshSidebar();
        });
    }

    private void showProfileDialog() {
        String username = controller.getUsername();
        String shown = displayName(username);
        Dialog<Void> d = new Dialog<>();
        d.setTitle("Mon profil");
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        d.getDialogPane().setStyle("-fx-background-color:" + BG_DARK + ";");

        VBox content = new VBox(0);
        content.setAlignment(Pos.TOP_CENTER);
        content.setMinWidth(360);
        content.setStyle("-fx-background-color:" + BG_DARK + ";");

        VBox header = new VBox(14);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(32, 24, 24, 24));
        header.setStyle("-fx-background-color:" + BG_PANEL + ";");

        StackPane avWrap = new StackPane();
        Circle ring = new Circle(52, Color.TRANSPARENT);
        ring.setStroke(Color.web(GREEN));
        ring.setStrokeWidth(2);
        Label bigAv = new Label(initial(shown));
        bigAv.setStyle(avStyle(avColor(username), 96));
        avWrap.getChildren().addAll(bigAv, ring);

        Label nameLbl = new Label(shown);
        nameLbl.setStyle("-fx-text-fill:" + TXT_WHITE + ";-fx-font-size:24px;-fx-font-weight:bold;");

        HBox statusRow = new HBox(8);
        statusRow.setAlignment(Pos.CENTER);
        Circle dot = new Circle(5, Color.web(isUserOnline(username) ? ONLINE_DOT : TXT_GRAY));
        Label statusLbl2 = new Label(isUserOnline(username) ? "En ligne" : "Hors ligne");
        statusLbl2.setStyle("-fx-text-fill:" + TXT_GRAY + ";-fx-font-size:14px;");
        statusRow.getChildren().addAll(dot, statusLbl2);

        header.getChildren().addAll(avWrap, nameLbl, statusRow);

        VBox infoBox = new VBox(0);
        infoBox.setPadding(new Insets(8, 0, 16, 0));
        addInfoRow(infoBox, "👤", "Nom", shown);
        addInfoRow(infoBox, "💬", "À propos", "Disponible");
        addInfoRow(infoBox, "📱", "Application", "MiniWhatsApp Java");

        content.getChildren().addAll(header, infoBox);
        d.getDialogPane().setContent(content);
        d.showAndWait();
    }

    /** Afficher dialog pour retirer un membre (admin seulement). */
    private void showRemoveMemberDialog() {
        if (selGroup == null) return;
        GroupInfo g = groups.get(selGroup);
        if (g == null) return;
        // Vérifier que l'utilisateur est admin
        if (!controller.getUsername().equals(g.admin)) {
            new Alert(Alert.AlertType.WARNING, "Seul l'administrateur peut retirer des membres.").showAndWait();
            return;
        }
        List<String> removable = new ArrayList<>(g.members);
        removable.remove(controller.getUsername()); // ne peut pas se retirer soi-même
        if (removable.isEmpty()) { new Alert(Alert.AlertType.INFORMATION, "Aucun membre à retirer.").showAndWait(); return; }

        showMemberPicker("Retirer un membre", removable, selGroup, true);
    }

    private HBox convSearchBar; // barre de recherche dans la conv
    private boolean convSearchVisible = false;

    private void toggleSearchInConv() {
        if (convSearchBar == null) return;
        convSearchVisible = !convSearchVisible;
        convSearchBar.setVisible(convSearchVisible);
        convSearchBar.setManaged(convSearchVisible);
        if (convSearchVisible) {
            for (Node n : convSearchBar.getChildren()) {
                if (n instanceof TextField sf) {
                    sf.clear();
                    sf.requestFocus();
                    break;
                }
            }
        } else {
            convSearchQuery = "";
            if (selUser != null) rebuildPrivPane(selUser);
            else if (selGroup != null) rebuildGrpPane(selGroup);
        }
    }

    private boolean matchesConvSearch(String text) {
        if (convSearchQuery == null || convSearchQuery.isBlank()) return true;
        return text != null && text.toLowerCase().contains(convSearchQuery);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  HEADER BUTTONS
    // ══════════════════════════════════════════════════════════════════════

    private void hideHdrBtns() {
        for (Node n : List.of(btnAudio, btnVideo, btnGrpAudio, btnGrpVideo, btnGrpAdd, btnGrpRemove))
            { n.setVisible(false); n.setManaged(false); }
    }

    private void updateHdrBtns() {
        hideHdrBtns();
        if (mode == Mode.CHATS && selUser != null) setVis(btnAudio, btnVideo);
        else if (mode == Mode.GROUPS && selGroup != null) {
            setVis(btnGrpAudio, btnGrpVideo, btnGrpAdd);
            GroupInfo g = groups.get(selGroup);
            if (g != null && controller.getUsername().equals(g.admin)) {
                setVis(btnGrpRemove);
            }
        }
    }

    private void setVis(Node... nn) { for (Node n : nn) { n.setVisible(true); n.setManaged(true); } }

    // ══════════════════════════════════════════════════════════════════════
    //  PARSING
    // ══════════════════════════════════════════════════════════════════════

    private List<HistoryMsg> parseMsgs(String c) {
        List<HistoryMsg> r = new ArrayList<>();
        if (c==null||c.isBlank()) return r;
        for (String line : c.split("\\R")) {
            if (line.isBlank()) continue;
            String[] p = line.split("\\t",-1);
            if (p.length >= 5 && p[0].matches("\\d+")) {
                r.add(new HistoryMsg(Integer.parseInt(p[0]), p[1], p[2], p[3], decode(p[4])));
            } else if (p.length >= 4) {
                r.add(new HistoryMsg(p[0], p[1], p[2], decode(p[3])));
            }
        }
        return r;
    }

    private List<HistoryCallItem> parseCalls(String c) {
        List<HistoryCallItem> r = new ArrayList<>();
        if (c==null||c.isBlank()) return r;
        for (String line : c.split("\\R")) {
            if (line.isBlank()) continue;
            String[] p = line.split("\\t",-1); if (p.length<7) continue;
            int dur = 0; try { dur = Integer.parseInt(p[6]); } catch(Exception ignored) {}
            r.add(new HistoryCallItem(p[0],p[1],p[2],p[3],p[4],p[5],dur));
        }
        return r;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  UTILITAIRES
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Normalise un timestamp MySQL/JDBC pour tri fiable.
     * Corrige le bug où "2026-05-18 18:05:00" (messages) triait avant
     * "2026-05-18 18:05:00.0" (fichiers) en comparaison de chaînes.
     */
    private static String normalizeTimestamp(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        String s = raw.trim().replace('T', ' ');
        int dot = s.indexOf('.');
        if (dot > 0) s = s.substring(0, dot);
        if (s.length() > 19) s = s.substring(0, 19);
        return s;
    }

    /** Clé de tri en millisecondes (ordre chronologique réel). */
    private static long chronosKey(String t) {
        String n = normalizeTimestamp(t);
        if (n == null || n.isBlank()) return Long.MAX_VALUE;
        try {
            return LocalDateTime.parse(n, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception ignored) {}
        try {
            return LocalDateTime.parse(n, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception ignored) {}
        return Long.MAX_VALUE;
    }

    private String nowTime()   { return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")); }

    private String shortTime(String t) {
        if (t==null||t.isBlank()) return nowTime();
        try { if (t.length()>=16) return t.substring(11,16); } catch(Exception ignored) {}
        return t;
    }

    private String shortDate(String t) {
        if (t==null||t.isBlank()) return "";
        try {
            LocalDate d = LocalDate.parse(t.substring(0,10));
            LocalDate today = LocalDate.now();
            if (d.equals(today)) return shortTime(t);
            if (d.equals(today.minusDays(1))) return "Hier";
            return d.format(DateTimeFormatter.ofPattern("dd/MM"));
        } catch(Exception e) { return shortTime(t); }
    }

    private String dateLabel(String t) {
        if (t==null||t.isBlank()) return "Aujourd'hui";
        try {
            LocalDate d = LocalDate.parse(t.substring(0,10));
            LocalDate today = LocalDate.now();
            if (d.equals(today)) return "Aujourd'hui";
            if (d.equals(today.minusDays(1))) return "Hier";
            return d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch(Exception e) { return "Aujourd'hui"; }
    }

    private String decode(String s) {
        try { return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8); }
        catch(Exception e) { return s; }
    }

    private String fileType(String n) {
        String l = n.toLowerCase();
        if (l.matches(".*\\.(png|jpg|jpeg|gif|bmp|webp)")) return "image";
        if (l.endsWith(".pdf")) return "pdf";
        if (l.matches(".*\\.(mp4|avi|mkv|mov)")) return "video";
        return "file";
    }

    private void saveFile(String fn, byte[] data) {
        FileChooser fc = new FileChooser(); fc.setInitialFileName(fn);
        File dest = fc.showSaveDialog(stage); if (dest==null) return;
        try { java.nio.file.Files.write(dest.toPath(), data); }
        catch(IOException ex) { alert(ex.getMessage()); }
    }

    private void scrollBottom() { Platform.runLater(() -> chatScroll.setVvalue(1.0)); }
    private void alert(String msg) { new Alert(Alert.AlertType.ERROR, msg).showAndWait(); }


    private void addInfoRow(VBox box, String icon, String label, String value) {
        Label ico = new Label(icon); ico.setStyle("-fx-font-size:18px;-fx-min-width:28px;");
        VBox txt = new VBox(2);
        Label lbl = new Label(label); lbl.setStyle("-fx-text-fill:#8696A0;-fx-font-size:11px;");
        Label val = new Label(value); val.setStyle("-fx-text-fill:#E9EDEF;-fx-font-size:15px;");
        txt.getChildren().addAll(lbl, val); HBox.setHgrow(txt, Priority.ALWAYS);
        HBox row = new HBox(14, ico, txt);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-background-color:" + BG_DARK + ";-fx-padding:16 20;-fx-border-color:" + BORDER + ";-fx-border-width:0 0 1 0;");
        box.getChildren().add(row);
    }

    private void showAlert(String msg, Alert.AlertType type) {
        Alert a = new Alert(type); a.setContentText(msg); a.showAndWait();
    }

    private String displayName(String username) {
        return DisplayNames.toDisplay(username);
    }

    private String initial(String s) {
        String d = displayName(s);
        return (d == null || d.isEmpty()) ? "?" : d.substring(0, 1).toUpperCase();
    }

    /** Avatar groupe : icône centrée dans un cercle (style WhatsApp). */
    private StackPane groupAvatar(int size) {
        StackPane sp = new StackPane();
        sp.setMinSize(size, size);
        sp.setMaxSize(size, size);
        Circle bg = new Circle(size / 2.0, Color.web("#128C7E"));
        Label icon = new Label("👥");
        icon.setStyle("-fx-font-size:" + (int) (size * 0.38) + "px;-fx-text-fill:white;");
        sp.getChildren().addAll(bg, icon);
        return sp;
    }

    private Button mkWaNewGroupBtn() {
        StackPane g = groupAvatar(28);
        Button btn = new Button();
        btn.setGraphic(g);
        btn.setStyle("-fx-background-color:transparent;-fx-padding:0;-fx-cursor:hand;-fx-border-color:transparent;");
        return btn;
    }

    private Button mkHdrIconBtn(String kind) {
        StackPane icon = new StackPane();
        double r = 18;
        Circle bg = new Circle(r, Color.web(BG_INPUT));
        Label sym = new Label("user-add".equals(kind) ? "+" : "−");
        sym.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:" + TXT_WHITE + ";");
        Label person = new Label("👤");
        person.setStyle("-fx-font-size:11px;-fx-translate-y:-3;");
        icon.getChildren().addAll(bg, person, sym);
        if ("user-add".equals(kind)) {
            sym.setTranslateX(8);
            sym.setTranslateY(-8);
        } else {
            sym.setTranslateX(9);
            sym.setTranslateY(0);
        }
        Button b = new Button();
        b.setGraphic(icon);
        b.setStyle("-fx-background-color:transparent;-fx-padding:2;-fx-cursor:hand;-fx-border-color:transparent;");
        return b;
    }

    private String avColor(String s) {
        String[] cols = {"#E53935","#D81B60","#8E24AA","#5E35B1","#1E88E5","#00897B","#43A047","#F4511E"};
        return cols[Math.abs(s.hashCode()) % cols.length];
    }

    private String avStyle(String color, int size) {
        return "-fx-background-color:" + color + ";-fx-text-fill:white;-fx-font-weight:bold;" +
               "-fx-font-size:" + (int)(size/2.4) + "px;" +
               "-fx-min-width:" + size + "px;-fx-min-height:" + size + "px;" +
               "-fx-max-width:" + size + "px;-fx-max-height:" + size + "px;" +
               "-fx-background-radius:50%;-fx-alignment:center;";
    }

    private Label navIcon(String icon, String tip) {
        Label l = new Label(icon);
        l.setStyle("-fx-font-size:20px;-fx-cursor:hand;-fx-padding:12 8;-fx-background-radius:10;-fx-text-fill:" + TXT_GRAY + ";");
        if (tip != null) l.setTooltip(new Tooltip(tip));
        return l;
    }

    private Label mkTxtBtn(String icon) {
        Label l = new Label(icon);
        l.setStyle("-fx-font-size:18px;-fx-text-fill:" + TXT_GRAY + ";-fx-cursor:hand;-fx-padding:4 8;");
        return l;
    }

    private Button mkCircleBtn(String icon, String color, int size) {
        Button b = new Button(icon);
        b.setStyle("-fx-background-color:" + color + ";-fx-text-fill:white;" +
                   "-fx-background-radius:50%;-fx-min-width:" + size + "px;-fx-min-height:" + size + "px;" +
                   "-fx-max-width:" + size + "px;-fx-max-height:" + size + "px;" +
                   "-fx-font-size:" + (size/3) + "px;-fx-cursor:hand;-fx-border-color:transparent;");
        return b;
    }

    private Region hpad(int h) { Region r = new Region(); r.setMinHeight(h); r.setMaxHeight(h); return r; }

    // ══════════════════════════════════════════════════════════════════════
    //  CLASSES INTERNES
    // ══════════════════════════════════════════════════════════════════════

    static class GroupInfo {
        String name, admin;
        final List<String> members = new ArrayList<>();
        GroupInfo(String id, String n, String a) { name=n; admin=a; }
    }

    /** Entrée dans la liste globale des appels (mode 📞). */
    public static class CallLogEntry {
        public final String peer, type, status, date;
        public final int dur;
        public final boolean fromMe;
        public CallLogEntry(String peer, String type, String status, String date, int dur, boolean fromMe) {
            this.peer=peer; this.type=type; this.status=status; this.date=date; this.dur=dur; this.fromMe=fromMe;
        }
    }

    private static class HistoryEntry {
        final String time; final Node node;
        HistoryEntry(String time, Node node) { this.time=time; this.node=node; }
    }
    private static class HistoryMsg {
        int id = -1;
        String sender, receiver, time, text;
        HistoryMsg(String s, String r, String t, String x) { sender=s; receiver=r; time=t; text=x; }
        HistoryMsg(int id, String s, String r, String t, String x) {
            this.id=id; sender=s; receiver=r; time=t; text=x;
        }
    }
    private static class HistoryCallItem {
        String peer, caller, callee, type, status, startedAt; int dur;
        HistoryCallItem(String p,String ca,String ce,String t,String st,String sa,int d) { peer=p; caller=ca; callee=ce; type=t; status=st; startedAt=sa; dur=d; }
    }
}
