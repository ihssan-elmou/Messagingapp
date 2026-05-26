package org.example.client.controller;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.ImageView;
import org.example.client.media.*;
import org.example.client.network.RequestSender;
import org.example.client.ui.CallView;
import org.example.client.ui.ChatView;
import org.example.client.ui.IncomingCallScreen;
import org.example.model.CallRequest;
import org.example.protocol.Message;
import org.example.utils.Constants;

import java.util.Optional;

public class CallController {

    private String               username;
    private RequestSender        sender;
    private ChatView             chatView;
    private CallView             callView;

    private AudioCapture         audioCapture;
    private AudioPlayer          audioPlayer;
    private VideoCapture         videoCapture;
    private VideoPlayer          videoPlayer;

    private boolean              inCall          = false;
    private String               callPeer        = null;
    private CallRequest.CallType currentCallType = CallRequest.CallType.AUDIO;
    private String               serverHost;

    public CallController(String username, RequestSender sender, String serverHost) {
        this.username   = username;
        this.sender     = sender;
        this.serverHost = serverHost;
    }

    public void setChatView(ChatView v) { this.chatView = v; }

    public void initiateCall(String peer, CallRequest.CallType type) {
        if (peer == null) return;
        if (inCall) { showAlert("Déjà en appel !", Alert.AlertType.WARNING); return; }

        currentCallType = type;
        callPeer        = peer;

        try {
            audioPlayer = new AudioPlayer();
            new Thread(audioPlayer, "audio-player").start();
        } catch (Exception e) { e.printStackTrace(); return; }

        CallRequest req = new CallRequest(username, peer, type,
                Constants.AUDIO_PORT, audioPlayer.getPort());
        sender.sendCall(req);
        openCallView(peer, type, false);
    }

    public void onIncomingCall(CallRequest req) {
        if (inCall) { sender.rejectCall(req.getCaller()); return; }

        // Afficher l'écran d'appel entrant style WhatsApp
        javafx.application.Platform.runLater(() -> {
            showIncomingCallScreen(req);
        });
    }

    private void showIncomingCallScreen(CallRequest req) {
        boolean isVideo = req.getCallType() == CallRequest.CallType.VIDEO;
        String title = isVideo ? "Appel vidéo entrant" : "Appel vocal entrant";
        String subtitle = isVideo ? "Appel vidéo entrant..." : "Appel vocal entrant...";

        IncomingCallScreen.show(
                title,
                req.getCaller(),
                subtitle,
                () -> {
                    if (inCall) { sender.rejectCall(req.getCaller()); return; }
                    try {
                        audioPlayer = new AudioPlayer();
                        new Thread(audioPlayer, "audio-player").start();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        return;
                    }
                    currentCallType = req.getCallType();
                    sender.acceptCall(req.getCaller(), audioPlayer.getPort());
                    openCallView(req.getCaller(), req.getCallType(), true);
                    startMedia(req);
                    if (callView != null) {
                        callView.setStatus("En appel ✓");
                        callView.startTimer();
                    }
                },
                () -> sender.rejectCall(req.getCaller())
        );
    }

    public void onCallAccepted(Message msg) {
        if (inCall) {
            sender.registerAudioPort(msg.getSender(), audioPlayer.getPort());
            if (callView != null) callView.setStatus("En appel ✓");
            return;
        }
        sender.registerAudioPort(msg.getSender(), audioPlayer.getPort());
        CallRequest fakeReq = new CallRequest(
                username, msg.getSender(),
                currentCallType,
                Constants.AUDIO_PORT, audioPlayer.getPort());
        startMedia(fakeReq);
        if (callView != null) callView.setStatus("En appel ✓");
    }

    public void onCallRejected(Message msg) {
        stopMedia();
        callPeer = null;
        if (callView != null) callView.close();
        if (chatView != null) chatView.setHeaderStatus(msg.getSender() + " a refusé l'appel.");
    }

    public void onCallEnded(Message msg) {
        stopMedia();
        if (callView != null) callView.close();
        if (chatView != null) {
            chatView.setHeaderStatus(msg.getSender() + " a terminé l'appel.");
            String peer = callPeer != null ? callPeer : msg.getSender();
            chatView.recordCallActivity(peer, currentCallType.name());
        }
        callPeer = null;
    }

    public void endCall() {
        if (!inCall || callPeer == null) return;
        String peer = callPeer;
        sender.endCall(callPeer);
        stopMedia();
        if (callView != null) callView.close();
        if (chatView != null) chatView.recordCallActivity(peer, currentCallType.name());
        callPeer = null;
    }

    private void startMedia(CallRequest req) {
        if (audioCapture != null) return;

        inCall   = true;
        callPeer = req.getCaller().equals(username)
                ? req.getCallee() : req.getCaller();

        audioCapture = new AudioCapture(serverHost, Constants.AUDIO_PORT, username);
        new Thread(audioCapture, "audio-capture").start();

        if (req.getCallType() == CallRequest.CallType.VIDEO) {
            try {
                ImageView remoteDisplay = callView != null
                        ? callView.getVideoView()      : new ImageView();
                ImageView localDisplay  = callView != null
                        ? callView.getLocalVideoView() : new ImageView();

                videoPlayer = new VideoPlayer(remoteDisplay);
                new Thread(videoPlayer, "video-player").start();
                sender.registerVideoPort(callPeer, videoPlayer.getPort());

                // VideoCapture avec aperçu local
                videoCapture = new VideoCapture(serverHost, Constants.VIDEO_PORT,
                        username, localDisplay);
                new Thread(videoCapture, "video-capture").start();

            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private void stopMedia() {
        inCall   = false;
        callPeer = null;
        if (audioCapture != null) { audioCapture.stop(); audioCapture = null; }
        if (audioPlayer  != null) { audioPlayer.stop();  audioPlayer  = null; }
        if (videoCapture != null) { videoCapture.stop(); videoCapture = null; }
        if (videoPlayer  != null) { videoPlayer.stop();  videoPlayer  = null; }
    }

    private void openCallView(String peer, CallRequest.CallType type, boolean isIncoming) {
        callView = new CallView(peer, type, this, isIncoming);
        callView.setOnMuteToggle(() -> {
            if (audioCapture != null)
                audioCapture.setMuted(!audioCapture.isMuted());
        });
        callView.show();
    }

    private void showAlert(String msg, Alert.AlertType type) {
        Alert a = new Alert(type);
        a.setContentText(msg);
        a.showAndWait();
    }

    public boolean isInCall() { return inCall; }

    /** Serveur indisponible : fermer l'appel sans envoyer au serveur. */
    public void onServerLost() {
        stopMedia();
        if (callView != null) callView.close();
        callPeer = null;
    }
}