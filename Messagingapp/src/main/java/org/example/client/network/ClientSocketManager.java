package org.example.client.network;

import org.example.protocol.*;
import org.example.protocol.GroupFileMessage;
import org.example.model.CallRequest;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gère la connexion TCP avec le serveur.
 * Sépare l'I/O réseau de la logique UI.
 */
public class ClientSocketManager {

    private Socket             socket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;
    private String             username;
    private ClientListener     listener;
    private final String       host;
    private final int          port;
    private final String       password;
    private volatile boolean   intentionalDisconnect;
    private final AtomicBoolean disconnectNotified = new AtomicBoolean(false);
    private volatile Thread    receiverThread;

    public ClientSocketManager(String username, String password, String host,
                               int port, ClientListener listener, boolean register)
            throws IOException {
        this.username = username;
        this.listener = listener;
        this.host = host;
        this.port = port;
        this.password = password != null ? password : "";
        connect(register);
    }

    private void connect(boolean register) throws IOException {
        intentionalDisconnect = false;
        disconnectNotified.set(false);
        socket = new Socket(host, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());

        MessageType type = register ? MessageType.REGISTER : MessageType.LOGIN;
        send(new Message(type, username, null, password));

        Object first = readObjectSafe();
        if (first instanceof Message msg && msg.getType() == MessageType.ERROR) {
            String reason = msg.getContent() != null ? msg.getContent() : "Connexion refusée";
            if (reason.startsWith("AUTH:")) reason = reason.substring(5).trim();
            closeConnectionQuietly();
            throw new IOException(reason);
        }
        if (first instanceof Message msg) {
            applyAuthOk(msg);
            if (listener != null) listener.onMessage(msg);
        }

        startReceiverThread();
    }

    /** Reconnexion après perte du serveur (LOGIN uniquement). */
    public synchronized void reconnect() throws IOException {
        closeConnectionQuietly();
        intentionalDisconnect = false;
        disconnectNotified.set(false);

        socket = new Socket(host, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());

        send(new Message(MessageType.LOGIN, username, null, password));

        Object first = readObjectSafe();
        if (first instanceof Message msg && msg.getType() == MessageType.ERROR) {
            String reason = msg.getContent() != null ? msg.getContent() : "Connexion refusée";
            if (reason.startsWith("AUTH:")) reason = reason.substring(5).trim();
            closeConnectionQuietly();
            throw new IOException(reason);
        }
        if (first instanceof Message msg) {
            applyAuthOk(msg);
            if (listener != null) listener.onMessage(msg);
        }

        startReceiverThread();
    }

    private void startReceiverThread() {
        Thread t = new Thread(this::receiveLoop, "receiver-" + username);
        t.setDaemon(true);
        receiverThread = t;
        t.start();
    }

    /** {@link ObjectInputStream#readObject()} convertit ClassNotFoundException en IOException. */
    private Object readObjectSafe() throws IOException {
        if (in == null) throw new IOException("Non connecté");
        try {
            return in.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException("Réponse serveur invalide : " + e.getMessage(), e);
        }
    }

    /** @return true si l'objet a été envoyé, false si hors ligne ou erreur réseau */
    public synchronized boolean send(Object obj) {
        if (out == null || socket == null || socket.isClosed()) {
            return false;
        }
        try {
            out.writeObject(obj);
            out.flush();
            out.reset();
            return true;
        } catch (IOException e) {
            closeConnectionQuietly();
            notifyDisconnected();
            return false;
        }
    }

    private void receiveLoop() {
        try {
            Object obj;
            while ((obj = readObjectSafe()) != null) {
                if (obj instanceof GroupFileMessage gfm) {
                    if (listener instanceof org.example.client.controller.ChatController cc) {
                        cc.onGroupFileReceived(gfm);
                    }
                } else if (obj instanceof Message msg) {
                    applyAuthOk(msg);
                    listener.onMessage(msg);
                } else if (obj instanceof CallRequest req) {
                    listener.onCallRequest(req);
                } else if (obj instanceof FileMessage fm) {
                    listener.onFileReceived(fm);
                }
            }
        } catch (IOException e) {
            notifyDisconnected();
        }
    }

    public void disconnect() {
        intentionalDisconnect = true;
        try {
            if (socket != null && !socket.isClosed() && out != null) {
                send(new Message(MessageType.LOGOUT, username, null, ""));
            }
        } catch (Exception ignored) {}
        closeConnectionQuietly();
    }

    private void closeConnectionQuietly() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
        socket = null;
        out = null;
        in = null;
    }

    private void notifyDisconnected() {
        if (intentionalDisconnect) return;
        if (!disconnectNotified.compareAndSet(false, true)) return;
        if (listener != null) listener.onDisconnected();
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public boolean isIntentionalDisconnect() {
        return intentionalDisconnect;
    }

    public String getUsername() { return username; }

    public void setUsername(String username) {
        if (username != null && !username.isBlank()) this.username = username;
    }

    private void applyAuthOk(Message msg) {
        if (msg.getType() != MessageType.INFO || msg.getContent() == null) return;
        if (!msg.getContent().startsWith("AUTH_OK:")) return;
        if (msg.getReceiver() != null && !msg.getReceiver().isBlank()) {
            this.username = msg.getReceiver();
        }
    }

    public String getHost() {
        return host;
    }

    /** Décode le preview Base64 renvoyé par CONTACTS_RESPONSE. */
    public static String decodeContactPreview(String encoded) {
        if (encoded == null || encoded.isBlank()) return "";
        try {
            return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return encoded;
        }
    }
}
