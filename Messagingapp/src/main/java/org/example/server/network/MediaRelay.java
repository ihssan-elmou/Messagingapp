package org.example.server.network;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MediaRelay implements Runnable {

    private static final int BUFFER = 65536;

    private final int port;

    /*
     * username -> adresse UDP où ce client écoute
     * Exemple :
     * user1 -> IP machine user1 + port AudioPlayer/VideoPlayer
     */
    private final Map<String, InetSocketAddress> listenAddrs = new ConcurrentHashMap<>();

    public MediaRelay(int port) {
        this.port = port;
    }

    public void registerPeer(String username, InetAddress ip, int listenPort) {
        if (username == null || username.trim().isEmpty()) {
            System.out.println("[MediaRelay port=" + port + "] register ignoré : username vide");
            return;
        }

        if (ip == null) {
            System.out.println("[MediaRelay port=" + port + "] register ignoré : IP null pour " + username);
            return;
        }

        if (listenPort <= 0) {
            System.out.println("[MediaRelay port=" + port + "] register ignoré : port invalide pour "
                    + username + " port=" + listenPort);
            return;
        }

        listenAddrs.put(username, new InetSocketAddress(ip, listenPort));

        System.out.println("[MediaRelay port=" + port + "] enregistré "
                + username
                + " -> IP=" + ip.getHostAddress()
                + " port=" + listenPort);
    }

    public void unregisterPeer(String username) {
        if (username == null) {
            return;
        }

        listenAddrs.remove(username);

        System.out.println("[MediaRelay port=" + port + "] désenregistré " + username);
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(port)) {

            System.out.println("[MediaRelay] UDP démarré sur port " + port);

            while (true) {
                byte[] buffer = new byte[BUFFER];
                DatagramPacket pkt = new DatagramPacket(buffer, buffer.length);

                socket.receive(pkt);

                byte[] data = Arrays.copyOf(pkt.getData(), pkt.getLength());

                System.out.println("[MediaRelay port=" + port + "] paquet reçu depuis "
                        + pkt.getAddress().getHostAddress()
                        + ":" + pkt.getPort()
                        + " taille=" + pkt.getLength());

                String sender;

                try {
                    sender = readUsername(data);
                } catch (Exception ex) {
                    System.out.println("[MediaRelay port=" + port + "] paquet invalide ignoré : "
                            + ex.getMessage());
                    continue;
                }

                System.out.println("[MediaRelay port=" + port + "] paquet de "
                        + sender
                        + " | peers enregistrés=" + listenAddrs.size());

                /*
                 * Correction importante :
                 * Si l'adresse IP réelle du paquet UDP est différente de l'adresse IP
                 * enregistrée depuis la connexion TCP, on corrige seulement l'IP.
                 * On ne remplace PAS le port, car pkt.getPort() est souvent le port d'envoi,
                 * pas le port d'écoute AudioPlayer/VideoPlayer.
                 */
                updateSenderIpIfNeeded(sender, pkt.getAddress());

                if (listenAddrs.size() <= 1) {
                    System.out.println("[MediaRelay port=" + port
                            + "] pas assez de peers pour relayer le paquet");
                    continue;
                }

                for (Map.Entry<String, InetSocketAddress> entry : listenAddrs.entrySet()) {
                    String destUsername = entry.getKey();

                    // Ne pas renvoyer le paquet au même utilisateur
                    if (destUsername.equals(sender)) {
                        continue;
                    }

                    InetSocketAddress destAddress = entry.getValue();

                    if (destAddress == null || destAddress.getAddress() == null) {
                        System.out.println("[MediaRelay port=" + port
                                + "] destination invalide pour " + destUsername);
                        continue;
                    }

                    InetAddress destIp = destAddress.getAddress();
                    int destPort = destAddress.getPort();

                    try {
                        DatagramPacket out = new DatagramPacket(
                                data,
                                data.length,
                                destIp,
                                destPort
                        );

                        socket.send(out);

                        System.out.println("[MediaRelay port=" + port + "] envoyé vers "
                                + destUsername
                                + " -> "
                                + destIp.getHostAddress()
                                + ":" + destPort
                                + " taille=" + data.length);

                    } catch (Exception sendEx) {
                        System.out.println("[MediaRelay port=" + port
                                + "] erreur envoi vers "
                                + destUsername
                                + " : "
                                + sendEx.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("[MediaRelay port=" + port + "] erreur générale : " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateSenderIpIfNeeded(String sender, InetAddress realPacketIp) {
        InetSocketAddress oldAddress = listenAddrs.get(sender);

        if (oldAddress == null) {
            return;
        }

        InetAddress oldIp = oldAddress.getAddress();

        if (oldIp == null || !oldIp.equals(realPacketIp)) {
            InetSocketAddress newAddress = new InetSocketAddress(
                    realPacketIp,
                    oldAddress.getPort()
            );

            listenAddrs.put(sender, newAddress);

            System.out.println("[MediaRelay port=" + port + "] IP corrigée pour "
                    + sender
                    + " : "
                    + realPacketIp.getHostAddress()
                    + " port conservé="
                    + oldAddress.getPort());
        }
    }

    private String readUsername(byte[] data) {
        if (data == null || data.length < 4) {
            throw new IllegalArgumentException("paquet trop court");
        }

        int len = ((data[0] & 0xFF) << 24)
                | ((data[1] & 0xFF) << 16)
                | ((data[2] & 0xFF) << 8)
                | (data[3] & 0xFF);

        if (len <= 0 || len > 100) {
            throw new IllegalArgumentException("longueur username invalide : " + len);
        }

        if (4 + len > data.length) {
            throw new IllegalArgumentException("username incomplet dans le paquet");
        }

        return new String(data, 4, len, StandardCharsets.UTF_8);
    }
}