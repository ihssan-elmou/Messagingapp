package org.example.client.media;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.bytedeco.javacv.*;
import org.example.utils.Constants;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class VideoCapture implements Runnable {

    private String             serverHost;
    private int                serverPort;
    private String             username;
    private volatile boolean   running = true;
    private DatagramSocket     socket;
    private OpenCVFrameGrabber grabber;
    private ImageView          localView;

    public VideoCapture(String serverHost, int serverPort,
                        String username, ImageView localView) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.username   = username;
        this.localView  = localView;
    }

    @Override
    public void run() {
        grabber = new OpenCVFrameGrabber(0);
        Java2DFrameConverter converter = new Java2DFrameConverter();

        try {
            grabber.setImageWidth(Constants.VIDEO_W);
            grabber.setImageHeight(Constants.VIDEO_H);
            // ← supprimé : grabber.setOption("vcodec", "mjpeg") — bloque sur certaines machines
            grabber.start();

            socket = new DatagramSocket();
            byte[] nameBytes = username.getBytes(StandardCharsets.UTF_8);

            System.out.println("[VideoCapture] Caméra démarrée");

            while (running) {
                Frame frame = grabber.grab();
                if (frame == null || frame.image == null) continue;

                java.awt.image.BufferedImage img = converter.convert(frame);
                if (img == null) continue;

                // Aperçu local
                if (localView != null) {
                    ByteArrayOutputStream localBaos = new ByteArrayOutputStream();
                    javax.imageio.ImageIO.write(img, "jpg", localBaos);
                    byte[] localData = localBaos.toByteArray();
                    Platform.runLater(() -> {
                        Image fxImg = new Image(new ByteArrayInputStream(localData));
                        if (!fxImg.isError()) localView.setImage(fxImg);
                    });
                }

                // Envoi au serveur
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                javax.imageio.ImageIO.write(img, "jpg", baos);
                byte[] imgData = baos.toByteArray();

                if (imgData.length > 60000) continue;

                ByteBuffer pkt = ByteBuffer.allocate(4 + nameBytes.length + imgData.length);
                pkt.putInt(nameBytes.length);
                pkt.put(nameBytes);
                pkt.put(imgData);

                byte[] data = pkt.array();
                socket.send(new DatagramPacket(data, data.length,
                        InetAddress.getByName(serverHost), serverPort));

                Thread.sleep(1000 / Constants.VIDEO_FPS);
            }

        } catch (Exception e) {
            if (running) e.printStackTrace();
        } finally {
            stopGrabber();
            if (socket != null && !socket.isClosed()) socket.close();
            System.out.println("[VideoCapture] Caméra arrêtée");
        }
    }

    private void stopGrabber() {
        try {
            if (grabber != null) {
                grabber.stop();
                grabber.release();
                grabber = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) socket.close();
        new Thread(this::stopGrabber).start();
    }
}