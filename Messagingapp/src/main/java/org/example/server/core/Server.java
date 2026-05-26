package org.example.server.core;

import org.example.server.network.MediaRelay;
import org.example.server.service.MeetingService;
import org.example.utils.Constants;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    private Map<String, ClientHandler> clients      = new ConcurrentHashMap<>();
    private GroupManager               groupManager = new GroupManager();
    private MediaRelay                 audioRelay   = new MediaRelay(Constants.AUDIO_PORT);
    private MediaRelay                 videoRelay   = new MediaRelay(Constants.VIDEO_PORT); // ← nouveau
    public void start() {
        new Thread(audioRelay, "audio-relay").start();
        new Thread(videoRelay, "video-relay").start();

        // Une seule instance partagée : sinon chaque client a sa propre liste de réunions
        MeetingService meetingService = new MeetingService(clients, groupManager, audioRelay, videoRelay);

        System.out.println("Serveur démarré sur port " + Constants.TEXT_PORT);
        try (ServerSocket ss = new ServerSocket(Constants.TEXT_PORT)) {
            while (true) {
                Socket socket = ss.accept();
                System.out.println("Nouveau client : " + socket.getInetAddress());
                new Thread(new ClientHandler(socket, clients, groupManager,
                        audioRelay, videoRelay, meetingService)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}