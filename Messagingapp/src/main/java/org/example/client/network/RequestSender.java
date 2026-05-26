package org.example.client.network;

import org.example.model.CallRequest;
import org.example.protocol.FileMessage;
import org.example.protocol.GroupFileMessage;
import org.example.protocol.Message;
import org.example.protocol.MessageType;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RequestSender {

    private final ClientSocketManager socket;
    private final Queue<Object>       pending = new ConcurrentLinkedQueue<>();

    public RequestSender(ClientSocketManager socket) {
        this.socket = socket;
    }

    public void sendText(String to, String text) {
        enqueueOrSend(new Message(
                MessageType.TEXT,
                socket.getUsername(),
                to,
                text
        ));
    }

    public void requestContacts() {
        socket.send(new Message(
                MessageType.LOAD_CONTACTS,
                socket.getUsername(),
                null,
                ""
        ));
    }

    public void requestHistory(String peer) {
        socket.send(new Message(
                MessageType.LOAD_HISTORY,
                socket.getUsername(),
                peer,
                ""
        ));
    }

    public void requestCallHistory(String peer) {
        socket.send(new Message(
                MessageType.LOAD_CALL_HISTORY,
                socket.getUsername(),
                peer,
                ""
        ));
    }

    public void sendMessage(Message msg) {
        if (isQueueable(msg)) enqueueOrSend(msg);
        else socket.send(msg);
    }

    public void sendCall(CallRequest req) {
        socket.send(req);
    }

    public void acceptCall(String caller, int myAudioPort) {
        socket.send(new Message(
                MessageType.CALL_ACCEPT,
                socket.getUsername(),
                caller,
                String.valueOf(myAudioPort)
        ));
    }

    public void rejectCall(String caller) {
        socket.send(new Message(
                MessageType.CALL_REJECT,
                socket.getUsername(),
                caller,
                "REJECT"
        ));
    }

    public void endCall(String peer) {
        socket.send(new Message(
                MessageType.CALL_END,
                socket.getUsername(),
                peer,
                "END"
        ));
    }

    public void registerAudioPort(String peer, int audioPort) {
        socket.send(new Message(
                MessageType.AUDIO_PORT_REGISTER,
                socket.getUsername(),
                peer,
                String.valueOf(audioPort)
        ));
    }

    public void registerVideoPort(String peer, int videoPort) {
        socket.send(new Message(
                MessageType.VIDEO_PORT_REGISTER,
                socket.getUsername(),
                peer,
                String.valueOf(videoPort)
        ));
    }

    public void sendFile(String to, String fileName, String fileType, byte[] data) {
        enqueueOrSend(new FileMessage(
                socket.getUsername(),
                to,
                fileName,
                fileType,
                data
        ));
    }

    public void sendRaw(Object obj) {
        if (obj instanceof GroupFileMessage) enqueueOrSend(obj);
        else socket.send(obj);
    }

    public void logout() {
        pending.clear();
        socket.disconnect();
    }

    /** Envoie les messages/fichiers en attente après reconnexion au serveur. */
    public int flushPending() {
        int sent = 0;
        while (!pending.isEmpty()) {
            Object next = pending.peek();
            if (!socket.send(next)) break;
            pending.poll();
            sent++;
        }
        return sent;
    }

    public int pendingCount() {
        return pending.size();
    }

    private void enqueueOrSend(Object obj) {
        if (socket.send(obj)) return;
        pending.offer(obj);
    }

    private static boolean isQueueable(Message msg) {
        if (msg == null || msg.getType() == null) return false;
        return msg.getType() == MessageType.TEXT
                || msg.getType() == MessageType.GROUP_MESSAGE;
    }
}
