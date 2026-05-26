package org.example.client.media;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.example.utils.Constants;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * VideoPlayer V2 — supporte les flux multi-participants.
 *
 * Chaque paquet UDP contient : [4 bytes: nameLen][nameBytes][imageData]
 * Le username extrait du paquet est utilisé pour router le frame vers
 * le bon ImageView via le resolver fourni par MeetingController.
 */
public class VideoPlayer implements Runnable {

    private volatile boolean running = true;
    private DatagramSocket   socket;
    private final int        actualPort;

    // Resolver : username -> ImageView (fourni par MeetingController)
    private final Function<String, ImageView> viewResolver;

    // Fallback pour compatibilité ancienne API (appel 1-to-1)
    private final ImageView fallbackView;

    /**
     * Constructeur multi-participant (réunion groupe).
     * @param viewResolver fonction username → ImageView
     */
    public VideoPlayer(Function<String, ImageView> viewResolver) throws Exception {
        this.viewResolver = viewResolver;
        this.fallbackView = null;
        this.socket       = new DatagramSocket(0);
        this.actualPort   = socket.getLocalPort();
        System.out.println("[VideoPlayer] écoute sur port " + actualPort);
    }

    /**
     * Constructeur simple (appel 1-to-1, rétro-compatibilité).
     * @param display ImageView unique où afficher la vidéo distante
     */
    public VideoPlayer(ImageView display) throws Exception {
        this.viewResolver = null;
        this.fallbackView = display;
        this.socket       = new DatagramSocket(0);
        this.actualPort   = socket.getLocalPort();
        System.out.println("[VideoPlayer] écoute sur port " + actualPort);
    }

    public int getPort() { return actualPort; }

    @Override
    public void run() {
        try {
            byte[] buf = new byte[65507];
            System.out.println("[VideoPlayer] Prêt à recevoir vidéo");

            while (running) {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                socket.receive(pkt);

                byte[] data = pkt.getData();
                int    len  = pkt.getLength();
                if (len < 4) continue;

                // Lire le nom d'utilisateur expéditeur
                int nameLen = ByteBuffer.wrap(data, 0, 4).getInt();
                if (nameLen <= 0 || nameLen > 200) continue;
                int imgOffset = 4 + nameLen;
                if (imgOffset >= len) continue;

                String senderName = new String(data, 4, nameLen, StandardCharsets.UTF_8);
                byte[] imgData    = Arrays.copyOfRange(data, imgOffset, len);

                // Résoudre l'ImageView cible
                final ImageView target;
                if (viewResolver != null) {
                    target = viewResolver.apply(senderName);
                } else {
                    target = fallbackView;
                }
                if (target == null) continue; // participant inconnu ou pas encore dans la grille

                final byte[] imgCopy = imgData;
                Platform.runLater(() -> {
                    try {
                        Image img = new Image(new ByteArrayInputStream(imgCopy));
                        if (!img.isError()) {
                            target.setImage(img);
                            // Ne pas forcer VIDEO_W/H : la tuile MeetingView gère la taille
                            if (fallbackView != null) {
                                target.setFitWidth(Constants.VIDEO_W);
                                target.setFitHeight(Constants.VIDEO_H);
                                target.setPreserveRatio(true);
                            }
                        }
                    } catch (Exception ignored) {}
                });
            }
        } catch (Exception e) {
            if (running) e.printStackTrace();
        } finally {
            if (socket != null && !socket.isClosed()) socket.close();
            System.out.println("[VideoPlayer] Arrêté");
        }
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) socket.close();
    }
}
