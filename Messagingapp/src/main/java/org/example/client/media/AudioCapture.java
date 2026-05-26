package org.example.client.media;

import org.example.utils.Constants;
import javax.sound.sampled.*;
import java.net.*;
import java.nio.ByteBuffer;

public class AudioCapture implements Runnable {

    private String           serverHost;
    private int              serverPort;
    private String           username;
    private volatile boolean running = true;
    private volatile boolean muted   = false;  // ← nouveau
    private DatagramSocket   socket;
    private TargetDataLine   mic;

    private static final AudioFormat[] FORMATS = {
            new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 2, 4, 44100, false),
            new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 1, 2, 44100, false),
            new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 22050, 16, 1, 2, 22050, false),
            new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100,  8, 1, 1, 44100, false),
            new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 22050,  8, 1, 1, 22050, false),
            new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,  8000, 16, 1, 2,  8000, false),
            new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,  8000,  8, 1, 1,  8000, false),
    };

    public AudioCapture(String serverHost, int serverPort, String username) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.username   = username;
    }

    public void setMuted(boolean muted) { this.muted = muted; }
    public boolean isMuted()            { return muted; }

    private TargetDataLine openMic() {
        for (AudioFormat fmt : FORMATS) {
            try {
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
                TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
                line.open(fmt);
                line.start();
                System.out.println("[AudioCapture] Micro ouvert: "
                        + fmt.getSampleRate() + "Hz "
                        + fmt.getSampleSizeInBits() + "bit "
                        + fmt.getChannels() + "ch");
                return line;
            } catch (Exception e) {
                System.out.println("[AudioCapture] Format refusé: "
                        + fmt.getSampleRate() + "Hz "
                        + fmt.getSampleSizeInBits() + "bit "
                        + fmt.getChannels() + "ch → " + e.getMessage());
            }
        }
        return null;
    }

    @Override
    public void run() {
        try {
            socket = new DatagramSocket();
            mic    = openMic();

            if (mic == null) {
                System.err.println("[AudioCapture] Aucun format audio supporté !");
                return;
            }

            byte[] nameBytes = username.getBytes();
            byte[] buf       = new byte[Constants.BUFFER_SIZE / 4];

            System.out.println("[AudioCapture] Envoi vers " + serverHost + ":" + serverPort);

            while (running) {
                int n = mic.read(buf, 0, buf.length);
                if (n <= 0) continue;
                if (muted)  continue;  // ← ne pas envoyer si muet

                ByteBuffer pkt = ByteBuffer.allocate(4 + nameBytes.length + n);
                pkt.putInt(nameBytes.length);
                pkt.put(nameBytes);
                pkt.put(buf, 0, n);

                byte[] data = pkt.array();
                socket.send(new DatagramPacket(data, data.length,
                        InetAddress.getByName(serverHost), serverPort));
            }
        } catch (Exception e) {
            if (running) e.printStackTrace();
        } finally {
            if (mic != null) { mic.stop(); mic.close(); mic = null; }
            if (socket != null && !socket.isClosed()) socket.close();
        }
    }

    public void stop() {
        running = false;
        if (socket != null) socket.close();
    }
}