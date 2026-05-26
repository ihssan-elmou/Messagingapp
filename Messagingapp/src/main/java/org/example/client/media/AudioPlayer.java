package org.example.client.media;

import org.example.utils.Constants;
import javax.sound.sampled.*;
import java.net.*;

public class AudioPlayer implements Runnable {

    private static final AudioFormat[] FORMATS = {
            new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 2, 4, 44100, false),
            new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 1, 2, 44100, false),
            new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 22050, 16, 1, 2, 22050, false),
            new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100,  8, 1, 1, 44100, false),
            new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 22050,  8, 1, 1, 22050, false),
            new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,  8000, 16, 1, 2,  8000, false),
            new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,  8000,  8, 1, 1,  8000, false),
    };

    private volatile boolean running = true;
    private DatagramSocket   socket;
    private int              actualPort;
    private SourceDataLine   speaker;
    private AudioFormat      activeFormat;

    public AudioPlayer() throws Exception {
        this.socket     = new DatagramSocket(0);
        this.actualPort = socket.getLocalPort();
        System.out.println("[AudioPlayer] écoute sur port " + actualPort);
    }

    public int getPort() { return actualPort; }

    private SourceDataLine openSpeaker() {
        for (AudioFormat fmt : FORMATS) {
            try {
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
                SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(fmt);   // ← teste vraiment
                line.start();
                activeFormat = fmt;
                System.out.println("[AudioPlayer] Haut-parleur ouvert: "
                        + fmt.getSampleRate() + "Hz "
                        + fmt.getSampleSizeInBits() + "bit "
                        + fmt.getChannels() + "ch");
                return line;
            } catch (Exception e) {
                System.out.println("[AudioPlayer] Format refusé: "
                        + fmt.getSampleRate() + "Hz → " + e.getMessage());
            }
        }
        return null;
    }

    @Override
    public void run() {
        try {
            speaker = openSpeaker();
            if (speaker == null) {
                System.err.println("[AudioPlayer] Aucun format audio supporté !");
                return;
            }

            byte[] buf = new byte[Constants.BUFFER_SIZE * 2];
            System.out.println("[AudioPlayer] Prêt à jouer l'audio");

            while (running) {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                socket.receive(pkt);

                byte[] data = pkt.getData();
                int    len  = pkt.getLength();
                if (len < 4) continue;

                int nameLen     = java.nio.ByteBuffer.wrap(data, 0, 4).getInt();
                int audioOffset = 4 + nameLen;
                if (audioOffset >= len) continue;

                speaker.write(data, audioOffset, len - audioOffset);
            }
        } catch (Exception e) {
            if (running) e.printStackTrace();
        } finally {
            if (speaker != null) { speaker.drain(); speaker.close(); speaker = null; }
            if (socket != null && !socket.isClosed()) socket.close();
        }
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) socket.close();
        if (speaker != null) { speaker.stop(); speaker.close(); speaker = null; }
    }
}