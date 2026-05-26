package org.example.client.controller;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import org.example.client.media.AudioCapture;
import org.example.client.ui.IncomingCallScreen;
import org.example.client.media.AudioPlayer;
import org.example.client.media.VideoCapture;
import org.example.client.media.VideoPlayer;
import org.example.client.network.RequestSender;
import org.example.client.ui.MeetingView;
import org.example.protocol.Message;
import org.example.protocol.MessageType;
import org.example.utils.Constants;

public class MeetingController {

    private final String        username;
    private final RequestSender sender;
    private final String        serverHost;

    private MeetingView  meetingView;
    private String       currentMeetingId = null;
    private String       pendingGroupName = "";
    private boolean      inMeeting        = false;
    private boolean      videoMode        = false;

    private final java.util.List<String> pendingParticipants =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Arrivées reçues avant MEETING_CREATED / MEETING_JOINED_CONFIRM (course réseau). */
    private final java.util.List<PendingJoin> pendingJoins =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    private static final class PendingJoin {
        final String meetingId;
        final String username;
        PendingJoin(String meetingId, String username) {
            this.meetingId = meetingId;
            this.username = username;
        }
    }

    private AudioCapture audioCapture;
    private AudioPlayer  audioPlayer;
    private VideoCapture videoCapture;
    private VideoPlayer  videoPlayer;

    public MeetingController(String username, RequestSender sender, String serverHost) {
        this.username   = username;
        this.sender     = sender;
        this.serverHost = serverHost;
    }

    public void startMeeting(String groupId, String groupName) {
        if (inMeeting) { showAlert("Vous êtes déjà dans une réunion.", Alert.AlertType.WARNING); return; }
        this.videoMode = false;
        this.pendingGroupName = groupName != null ? groupName : "";
        Platform.runLater(() -> openMeetingView(pendingGroupName));
        sender.sendMessage(new Message(MessageType.MEETING_START, username, null, groupId + "|AUDIO"));
    }

    public void startVideoMeeting(String groupId, String groupName) {
        if (inMeeting) { showAlert("Vous êtes déjà dans une réunion.", Alert.AlertType.WARNING); return; }
        this.videoMode = true;
        this.pendingGroupName = groupName != null ? groupName : "";
        Platform.runLater(() -> openMeetingView(pendingGroupName));
        sender.sendMessage(new Message(MessageType.MEETING_START, username, null, groupId + "|VIDEO"));
    }

    public void joinMeeting(String meetingId) {
        if (meetingId == null || meetingId.isBlank()) return;
        String id = meetingId.trim();
        currentMeetingId = id;
        if (!inMeeting) {
            startAudio();
        }
        sender.sendMessage(new Message(MessageType.MEETING_JOIN, username, null, id));
    }

    /** Raccrocher : quitte la réunion, notifie le serveur et ferme l'interface. */
    public void hangUp() {
        if (meetingView == null && currentMeetingId == null && !inMeeting) return;

        String meetingId = currentMeetingId;
        currentMeetingId = null;
        pendingParticipants.clear();
        pendingJoins.clear();

        if (meetingId != null) {
            sender.sendMessage(new Message(MessageType.MEETING_LEAVE, username, null, meetingId));
        }
        stopMedia();

        if (meetingView != null) {
            MeetingView view = meetingView;
            meetingView = null;
            view.close();
        }
    }

    public void leaveMeeting() {
        hangUp();
    }

    public void onMeetingCreated(String meetingIdWithType, String groupName) {
        String[] parts = meetingIdWithType.split(":", 2);
        currentMeetingId = parts[0].trim();
        if (parts.length >= 2) videoMode = "VIDEO".equals(parts[1]);
        String displayName = (groupName != null && !groupName.isBlank())
                ? groupName : pendingGroupName;
        if (!inMeeting) {
            startAudio();
        }
        Platform.runLater(() -> {
            if (meetingView == null) {
                openMeetingView(displayName);
            }
            applyPendingJoins();
            flushPendingParticipants();
        });
    }

    public void onMeetingInvite(String meetingId, String groupName, String initiator, String type) {
        Platform.runLater(() -> {
            if (inMeeting) return;
            boolean vid = "VIDEO".equals(type);
            String gName = groupName != null && !groupName.isBlank() ? groupName : "Groupe";
            String title = vid ? "Appel vidéo de groupe" : "Appel vocal de groupe";
            String subtitle = initiator + " a démarré un appel " + (vid ? "vidéo" : "vocal")
                    + " dans \"" + gName + "\"";

            IncomingCallScreen.show(
                    title,
                    gName,
                    subtitle,
                    () -> {
                        videoMode = vid;
                        openMeetingView(gName);
                        joinMeeting(meetingId);
                    },
                    () -> { /* refus : rien à envoyer au serveur */ }
            );
        });
    }

    public void onMeetingJoinConfirmed(String meetingId) {
        if (meetingId == null || meetingId.isBlank()) return;
        currentMeetingId = meetingId.trim();
        Platform.runLater(() -> {
            applyPendingJoins();
            flushPendingParticipants();
        });
    }

    /** Liste complète envoyée par le serveur à chaque join (sync fiable de la grille). */
    public void onMeetingRoster(String meetingId, String rosterCsv) {
        if (meetingId == null || rosterCsv == null) return;
        final String mid = meetingId.trim();
        Platform.runLater(() -> {
            if (currentMeetingId == null) {
                pendingJoins.add(new PendingJoin(mid, "__ROSTER__:" + rosterCsv));
                return;
            }
            if (!mid.equals(currentMeetingId)) return;
            applyRoster(rosterCsv);
        });
    }

    public void onParticipantJoined(String meetingId, String user) {
        if (meetingId == null || user == null || user.isBlank() || user.equals(username)) return;
        final String mid = meetingId.trim();
        final String peer = user.trim();
        if (currentMeetingId == null) {
            pendingJoins.add(new PendingJoin(mid, peer));
            return;
        }
        if (!mid.equals(currentMeetingId)) return;
        Platform.runLater(() -> addParticipantToView(peer));
    }

    private void applyRoster(String rosterCsv) {
        for (String part : rosterCsv.split(",")) {
            String u = part.trim();
            if (!u.isEmpty() && !u.equals(username)) {
                addParticipantToView(u);
            }
        }
    }

    private void applyPendingJoins() {
        if (currentMeetingId == null) return;
        for (PendingJoin pj : pendingJoins) {
            if (!currentMeetingId.equals(pj.meetingId)) continue;
            if (pj.username.startsWith("__ROSTER__:")) {
                applyRoster(pj.username.substring("__ROSTER__:".length()));
            } else {
                addParticipantToView(pj.username);
            }
        }
        pendingJoins.removeIf(pj -> currentMeetingId.equals(pj.meetingId));
    }

    /** Ajoute un participant à la grille, ou le met en file d'attente si la vue n'est pas encore ouverte. */
    private void addParticipantToView(String peer) {
        if (meetingView != null) {
            meetingView.addParticipant(peer);
        } else if (!pendingParticipants.contains(peer)) {
            pendingParticipants.add(peer);
        }
    }

    private void flushPendingParticipants() {
        if (meetingView == null) return;
        for (String pending : pendingParticipants) {
            meetingView.addParticipant(pending);
        }
        pendingParticipants.clear();
        meetingView.refreshParticipants();
    }

    public void onParticipantLeft(String meetingId, String user) {
        if (user == null || user.isBlank() || user.equals(username)) return;
        if (meetingId != null && currentMeetingId != null
                && !meetingId.trim().equals(currentMeetingId)) {
            return;
        }
        final String leftUser = user.trim();
        Platform.runLater(() -> {
            if (meetingView != null) {
                meetingView.removeParticipant(leftUser);
            }
        });
    }

    private void startAudio() {
        if (inMeeting) return;
        inMeeting = true;
        try {
            audioPlayer = new AudioPlayer();
            new Thread(audioPlayer, "meeting-audio-player").start();
            sender.registerAudioPort("MEETING", audioPlayer.getPort());
            audioCapture = new AudioCapture(serverHost, Constants.AUDIO_PORT, username);
            new Thread(audioCapture, "meeting-audio-capture").start();
        } catch (Exception e) { e.printStackTrace(); inMeeting = false; }
    }

    /**
     * Démarre la capture vidéo et le lecteur vidéo multi-participant.
     * Le VideoPlayer utilise un resolver par username pour afficher chaque flux
     * dans la tuile correcte de la grille.
     */
    public void startVideo() {
        if (!inMeeting || meetingView == null) return;
        try {
            // VideoPlayer multi-participant : résout le bon ImageView par username
            videoPlayer = new VideoPlayer(uname -> {
                if (meetingView == null || uname == null || uname.equals(username)) return null;
                return meetingView.getRemoteViewForUser(uname);
            });
            new Thread(videoPlayer, "meeting-video-player").start();
            sender.registerVideoPort("MEETING", videoPlayer.getPort());
            // Capture locale — affichage dans l'overlay local
            videoCapture = new VideoCapture(serverHost, Constants.VIDEO_PORT,
                    username, meetingView.getLocalVideoView());
            new Thread(videoCapture, "meeting-video-capture").start();
        } catch (Exception e) { e.printStackTrace(); }
    }

    /**
     * Arrête uniquement la capture vidéo (caméra coupée).
     * Le lecteur vidéo continue de recevoir les flux des autres participants.
     */
    public void stopVideo() {
        if (videoCapture != null) { videoCapture.stop(); videoCapture = null; }
        if (meetingView != null) meetingView.clearLocalVideo();
    }

    private void stopMedia() {
        inMeeting = false;
        if (audioCapture != null) { audioCapture.stop(); audioCapture = null; }
        if (audioPlayer  != null) { audioPlayer.stop();  audioPlayer  = null; }
        if (videoCapture != null) { videoCapture.stop(); videoCapture = null; }
        if (videoPlayer  != null) { videoPlayer.stop();  videoPlayer  = null; }
    }

    public void toggleMute() {
        if (audioCapture != null) audioCapture.setMuted(!audioCapture.isMuted());
    }

    private void openMeetingView(String groupName) {
        if (meetingView != null) return;
        meetingView = new MeetingView(groupName, username, this, videoMode);
        meetingView.addParticipant(username);
        flushPendingParticipants();
        meetingView.show();
        if (videoMode) {
            new Thread(() -> {
                try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                Platform.runLater(this::startVideo);
            }, "meeting-video-start").start();
        }
    }

    private void showAlert(String msg, Alert.AlertType type) {
        Alert a = new Alert(type); a.setContentText(msg); a.showAndWait();
    }

    public boolean isInMeeting()   { return inMeeting; }
    public boolean isAudioMuted()  { return audioCapture != null && audioCapture.isMuted(); }
    public boolean isVideoActive() { return videoCapture != null; }
}