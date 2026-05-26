package org.example.client.ui;

import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.example.client.controller.GroupController;
import org.example.client.controller.MeetingController;

import java.util.*;

/**
 * Version 2 — Interface complète de gestion des groupes.
 *
 * Fonctionnalités :
 *   - Créer un groupe
 *   - Ajouter / retirer des membres
 *   - Chat de groupe en temps réel
 *   - Historique des messages de groupe
 *   - Démarrer une réunion audio/vidéo
 */
public class GroupView {

    private final Stage             stage;
    private final String            myUsername;
    private final GroupController   groupController;
    private final MeetingController meetingController;

    // État
    private final Map<String, GroupInfo> groups = new LinkedHashMap<>(); // groupId -> info
    private       String                 selectedGroupId = null;

    // ---- Widgets ----
    private final ListView<String>     groupListView   = new ListView<>();
    private final VBox                 chatBox         = new VBox(6);
    private final ScrollPane           chatScroll      = new ScrollPane(chatBox);
    private final TextField            messageField    = new TextField();
    private final Label                groupTitle      = new Label("Sélectionnez un groupe");
    private final Label                memberCountLbl  = new Label("");
    private final VBox                 membersBox      = new VBox(6);

    // ---- Données internes ----
    /** Groupes connus : groupId -> (name, admin, members) */
    private static class GroupInfo {
        String name, admin;
        List<String> members = new ArrayList<>();
        List<String[]> messages = new ArrayList<>(); // {sender, text}
        GroupInfo(String id, String name, String admin) {
            this.name = name; this.admin = admin;
        }
    }

    public GroupView(String myUsername,
                     GroupController groupController,
                     MeetingController meetingController) {
        this.myUsername        = myUsername;
        this.groupController   = groupController;
        this.meetingController = meetingController;
        this.stage             = new Stage();
        buildUI();
    }

    // ------------------------------------------------------------------ //
    //  Construction UI
    // ------------------------------------------------------------------ //

    private void buildUI() {
        stage.setTitle("Groupes — " + myUsername);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #FFFFFF;");

        // ---- Panneau gauche : liste des groupes ----
        VBox leftPanel = buildLeftPanel();
        leftPanel.setMinWidth(230);
        leftPanel.setMaxWidth(230);

        // ---- Panneau central : chat du groupe ----
        VBox centerPanel = buildChatPanel();
        HBox.setHgrow(centerPanel, Priority.ALWAYS);

        // ---- Panneau droit : membres ----
        VBox rightPanel = buildMembersPanel();
        rightPanel.setMinWidth(180);
        rightPanel.setMaxWidth(180);

        HBox body = new HBox(0, leftPanel, centerPanel, rightPanel);
        root.setCenter(body);

        Scene scene = new Scene(root, 900, 620);
        stage.setScene(scene);
        stage.setMinWidth(700);
        stage.setMinHeight(480);
    }

    // ---- Panneau gauche ----

    private VBox buildLeftPanel() {
        VBox panel = new VBox(0);
        panel.setStyle("-fx-background-color: #FCE4EC; -fx-border-color: #F8BBD9; -fx-border-width: 0 1 0 0;");

        Label title = new Label("💬  Mes Groupes");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #E91E8C; -fx-padding: 14 12 10 12;");

        Button createBtn = new Button("＋  Créer un groupe");
        createBtn.setStyle(
                "-fx-background-color: #E91E8C; -fx-text-fill: white; -fx-font-weight: bold;" +
                "-fx-font-size: 12px; -fx-padding: 8 12 8 12; -fx-background-radius: 20; -fx-cursor: hand;" +
                "-fx-margin: 0 10 8 10;"
        );
        HBox btnRow = new HBox(createBtn);
        btnRow.setPadding(new Insets(0, 10, 8, 10));
        createBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(createBtn, Priority.ALWAYS);
        createBtn.setOnAction(e -> showCreateGroupDialog());

        groupListView.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        groupListView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                GroupInfo info = groups.get(item);
                setText(info != null ? "👥  " + info.name : item);
                setStyle("-fx-font-size: 13px; -fx-padding: 8 12 8 12;");
            }
        });
        groupListView.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, val) -> { if (val != null) selectGroup(val); });
        VBox.setVgrow(groupListView, Priority.ALWAYS);

        panel.getChildren().addAll(title, btnRow, groupListView);
        return panel;
    }

    // ---- Panneau central (chat) ----

    private VBox buildChatPanel() {
        VBox panel = new VBox(0);
        panel.setStyle("-fx-background-color: #FFF9FB;");

        // En-tête
        HBox header = new HBox(10);
        header.setStyle("-fx-background-color: #FCE4EC; -fx-padding: 12 16 12 16;");
        header.setAlignment(Pos.CENTER_LEFT);

        groupTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #333;");
        memberCountLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");

        HBox spacer = new HBox(); HBox.setHgrow(spacer, Priority.ALWAYS);

        Button meetingBtn = new Button("🎥 Réunion");
        meetingBtn.setStyle(
                "-fx-background-color: #E91E8C; -fx-text-fill: white; -fx-font-weight: bold;" +
                "-fx-font-size: 12px; -fx-padding: 7 14 7 14; -fx-background-radius: 20; -fx-cursor: hand;"
        );
        meetingBtn.setOnAction(e -> onStartMeeting());

        header.getChildren().addAll(groupTitle, memberCountLbl, spacer, meetingBtn);

        // Zone chat
        chatBox.setPadding(new Insets(12));
        chatScroll.setFitToWidth(true);
        chatScroll.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(chatScroll, Priority.ALWAYS);

        // Zone de saisie
        HBox inputRow = buildInputRow();

        panel.getChildren().addAll(header, chatScroll, inputRow);
        return panel;
    }

    private HBox buildInputRow() {
        HBox row = new HBox(8);
        row.setStyle("-fx-background-color: #FFF0F5; -fx-padding: 10 14 10 14;");
        row.setAlignment(Pos.CENTER);

        messageField.setPromptText("Message au groupe...");
        messageField.setStyle(
                "-fx-background-color: white; -fx-background-radius: 20;" +
                "-fx-border-color: #F8BBD9; -fx-border-radius: 20;" +
                "-fx-padding: 9 16 9 16; -fx-font-size: 13px;"
        );
        HBox.setHgrow(messageField, Priority.ALWAYS);
        messageField.setOnAction(e -> sendGroupMessage());

        Button sendBtn = new Button("➤");
        sendBtn.setStyle(
                "-fx-background-color: #E91E8C; -fx-text-fill: white; -fx-font-weight: bold;" +
                "-fx-font-size: 16px; -fx-padding: 8 14 8 14; -fx-background-radius: 20; -fx-cursor: hand;"
        );
        sendBtn.setOnAction(e -> sendGroupMessage());

        row.getChildren().addAll(messageField, sendBtn);
        return row;
    }

    // ---- Panneau droit (membres) ----

    private VBox buildMembersPanel() {
        VBox panel = new VBox(8);
        panel.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #F8BBD9; -fx-border-width: 0 0 0 1;");
        panel.setPadding(new Insets(12));

        Label title = new Label("Membres");
        title.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #E91E8C;");

        ScrollPane sp = new ScrollPane(membersBox);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(sp, Priority.ALWAYS);

        Button addBtn = new Button("＋ Ajouter membre");
        addBtn.setStyle(
                "-fx-background-color: #FCE4EC; -fx-text-fill: #E91E8C; -fx-font-weight: bold;" +
                "-fx-font-size: 12px; -fx-padding: 7 10 7 10; -fx-background-radius: 16; -fx-cursor: hand;"
        );
        addBtn.setMaxWidth(Double.MAX_VALUE);
        addBtn.setOnAction(e -> showAddMemberDialog());

        panel.getChildren().addAll(title, new Separator(), sp, addBtn);
        return panel;
    }

    // ------------------------------------------------------------------ //
    //  Interactions
    // ------------------------------------------------------------------ //

    private void selectGroup(String groupId) {
        selectedGroupId = groupId;
        GroupInfo info = groups.get(groupId);
        if (info == null) return;

        groupTitle.setText("👥  " + info.name);
        memberCountLbl.setText("  " + info.members.size() + " membre(s)");

        // Recharger le chat
        chatBox.getChildren().clear();
        for (String[] msg : info.messages) {
            appendMessageBubble(msg[0], msg[1]);
        }
        scrollToBottom();

        // Recharger les membres
        refreshMembersPanel(info);
    }

    private void refreshMembersPanel(GroupInfo info) {
        membersBox.getChildren().clear();
        for (String member : info.members) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);

            String initial = member.isEmpty() ? "?" : member.substring(0, 1).toUpperCase();
            Label avatar = new Label(initial);
            avatar.setStyle(
                    "-fx-background-color: " + (member.equals(myUsername) ? "#E91E8C" : "#F48FB1") + ";" +
                    "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px;" +
                    "-fx-min-width: 28; -fx-max-width: 28; -fx-min-height: 28; -fx-max-height: 28;" +
                    "-fx-alignment: center; -fx-background-radius: 14;"
            );

            Label nameLbl = new Label(member + (member.equals(info.admin) ? " ★" : "")
                    + (member.equals(myUsername) ? " (moi)" : ""));
            nameLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #333;");

            row.getChildren().addAll(avatar, nameLbl);

            // Si je suis l'admin, bouton retirer (pas pour soi-même)
            if (info.admin.equals(myUsername) && !member.equals(myUsername)) {
                Button removeBtn = new Button("✕");
                removeBtn.setStyle(
                        "-fx-background-color: transparent; -fx-text-fill: #ccc; -fx-cursor: hand;" +
                        "-fx-font-size: 11px; -fx-padding: 0 4 0 4;"
                );
                HBox spacer = new HBox(); HBox.setHgrow(spacer, Priority.ALWAYS);
                final String m = member;
                removeBtn.setOnAction(e -> groupController.removeMember(selectedGroupId, m));
                row.getChildren().addAll(spacer, removeBtn);
            }

            membersBox.getChildren().add(row);
        }
    }

    private void sendGroupMessage() {
        if (selectedGroupId == null) return;
        String text = messageField.getText().trim();
        if (text.isEmpty()) return;
        groupController.sendGroupMessage(selectedGroupId, text);
        // Affichage immédiat côté envoyeur
        appendGroupMessage(myUsername, text);
        messageField.clear();
    }

    private void onStartMeeting() {
        if (selectedGroupId == null) return;
        GroupInfo info = groups.get(selectedGroupId);
        if (info == null) return;
        meetingController.startMeeting(selectedGroupId, info.name);
    }

    // ---- Dialogs ----

    private void showCreateGroupDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Créer un groupe");
        dialog.setHeaderText("Nouveau groupe");
        dialog.setContentText("Nom du groupe :");
        dialog.showAndWait().ifPresent(name -> {
            if (!name.isBlank()) groupController.createGroup(name);
        });
    }

    private void showAddMemberDialog() {
        if (selectedGroupId == null) return;
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Ajouter un membre");
        dialog.setHeaderText("Ajouter au groupe");
        dialog.setContentText("Nom d'utilisateur :");
        dialog.showAndWait().ifPresent(username -> {
            if (!username.isBlank()) groupController.addMember(selectedGroupId, username);
        });
    }

    // ------------------------------------------------------------------ //
    //  Callbacks depuis ChatController (Platform.runLater déjà fait)
    // ------------------------------------------------------------------ //

    /** Un nouveau groupe a été créé (confirmation serveur). */
    public void onGroupCreated(String groupId, String groupName) {
        GroupInfo info = new GroupInfo(groupId, groupName, myUsername);
        info.members.add(myUsername);
        groups.put(groupId, info);
        refreshGroupList();
    }

    /** L'utilisateur a été ajouté à un groupe existant. */
    public void onGroupAdded(String groupId, String groupName) {
        if (!groups.containsKey(groupId)) {
            groups.put(groupId, new GroupInfo(groupId, groupName, "?"));
        }
        refreshGroupList();
    }

    /** Mise à jour de la liste complète des groupes (depuis GROUP_LIST). */
    public void onGroupList(String rawList) {
        // Format : "groupId:groupName:admin:member1,member2,...\n..."
        for (String line : rawList.split("\n")) {
            if (line.isBlank()) continue;
            String[] parts = line.split(":", 4);
            if (parts.length < 4) continue;
            String gId = parts[0], gName = parts[1], admin = parts[2];
            GroupInfo info = groups.computeIfAbsent(gId, k -> new GroupInfo(gId, gName, admin));
            info.name  = gName;
            info.admin = admin;
            info.members = new ArrayList<>(Arrays.asList(parts[3].split(",")));
        }
        refreshGroupList();
    }

    /** Message de groupe reçu. */
    public void onGroupMessage(String groupId, String sender, String text) {
        GroupInfo info = groups.get(groupId);
        if (info == null) return;
        info.messages.add(new String[]{sender, text});
        if (groupId.equals(selectedGroupId)) {
            appendMessageBubble(sender, text);
            scrollToBottom();
        }
    }

    /** Un membre a rejoint. */
    public void onMemberJoined(String groupId, String username) {
        GroupInfo info = groups.get(groupId);
        if (info != null && !info.members.contains(username)) {
            info.members.add(username);
            memberCountLbl.setText("  " + info.members.size() + " membre(s)");
            if (groupId.equals(selectedGroupId)) refreshMembersPanel(info);
        }
    }

    /** Un membre a quitté. */
    public void onMemberLeft(String groupId, String username) {
        GroupInfo info = groups.get(groupId);
        if (info != null) {
            info.members.remove(username);
            memberCountLbl.setText("  " + info.members.size() + " membre(s)");
            if (groupId.equals(selectedGroupId)) refreshMembersPanel(info);
        }
    }

    // ------------------------------------------------------------------ //
    //  Helpers
    // ------------------------------------------------------------------ //

    private void refreshGroupList() {
        Platform.runLater(() -> {
            groupListView.getItems().setAll(groups.keySet());
        });
    }

    private void appendGroupMessage(String sender, String text) {
        Platform.runLater(() -> appendMessageBubble(sender, text));
    }

    private void appendMessageBubble(String sender, String text) {
        boolean isMe = sender.equals(myUsername);

        HBox row = new HBox();
        row.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 8, 2, 8));

        VBox bubble = new VBox(2);
        bubble.setMaxWidth(320);

        if (!isMe) {
            Label senderLbl = new Label(sender);
            senderLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #E91E8C; -fx-font-weight: bold;");
            bubble.getChildren().add(senderLbl);
        }

        Label msgLbl = new Label(text);
        msgLbl.setWrapText(true);
        msgLbl.setMaxWidth(300);
        msgLbl.setStyle(isMe
                ? "-fx-background-color: #E91E8C; -fx-text-fill: white; -fx-padding: 8 12 8 12; -fx-background-radius: 16 16 4 16; -fx-font-size: 13px;"
                : "-fx-background-color: #FCE4EC; -fx-text-fill: #333; -fx-padding: 8 12 8 12; -fx-background-radius: 16 16 16 4; -fx-font-size: 13px;"
        );
        bubble.getChildren().add(msgLbl);

        row.getChildren().add(bubble);
        chatBox.getChildren().add(row);
    }

    private void scrollToBottom() {
        Platform.runLater(() -> chatScroll.setVvalue(1.0));
    }

    public void show()  { stage.show(); }
    public void close() { stage.close(); }

    public boolean isShowing() { return stage.isShowing(); }
}
