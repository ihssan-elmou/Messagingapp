package org.example.server.service;

import org.example.model.CallRequest;
import org.example.model.CallSession;
import org.example.protocol.Message;
import org.example.protocol.MessageType;
import org.example.server.core.ClientHandler;
import org.example.server.database.dao.CallDAO;
import org.example.server.database.entity.DbCall;
import org.example.server.network.MediaRelay;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CallService {

    private Map<String, ClientHandler> clients;
    private Map<String, CallSession> activeCalls = new ConcurrentHashMap<>();
    private Map<String, CallRequest> pendingCalls = new ConcurrentHashMap<>();
    private Map<String, Integer> activeCallIds = new ConcurrentHashMap<>();

    private CallDAO callDAO = new CallDAO();

    private MediaRelay audioRelay;
    private MediaRelay videoRelay;

    public CallService(Map<String, ClientHandler> clients,
                       MediaRelay audioRelay,
                       MediaRelay videoRelay) {
        this.clients = clients;
        this.audioRelay = audioRelay;
        this.videoRelay = videoRelay;
    }

    public void handleCallRequest(CallRequest req) {
        System.out.println("[CallService] CALL_REQUEST de " + req.getCaller()
                + " vers " + req.getCallee()
                + " audioListenPort=" + req.getAudioListenPort());

        pendingCalls.put(req.getCaller(), req);
        savePendingCall(req);

        // Enregistrer le port audio du caller
        ClientHandler callerHandler = clients.get(req.getCaller());

        if (callerHandler != null && req.getAudioListenPort() > 0) {
            audioRelay.registerPeer(
                    req.getCaller(),
                    callerHandler.getClientAddress(),
                    req.getAudioListenPort()
            );

            System.out.println("[CallService] Audio caller enregistré : "
                    + req.getCaller()
                    + " IP=" + callerHandler.getClientAddress().getHostAddress()
                    + " port=" + req.getAudioListenPort());
        }

        // Envoyer la demande d'appel au destinataire
        ClientHandler target = clients.get(req.getCallee());

        if (target != null) {
            target.sendObject(req);
        } else {
            System.out.println("[CallService] Destinataire non connecté : " + req.getCallee());
            Integer callId = getCallId(req.getCaller(), req.getCallee());
            if (callId != null) {
                callDAO.markMissed(callId);
                if (req.getCaller() != null) activeCallIds.remove(req.getCaller());
                if (req.getCallee() != null) activeCallIds.remove(req.getCallee());
            }
        }
    }

    private void savePendingCall(CallRequest req) {
        String callType = req.getCallType() != null ? req.getCallType().name() : "AUDIO";

        int callId = callDAO.createAndReturnId(new DbCall(
                req.getCaller(),
                req.getCallee(),
                callType,
                "PENDING"
        ));

        if (callId > 0) {
            activeCallIds.put(req.getCaller(), callId);
            activeCallIds.put(req.getCallee(), callId);
        }
    }

    public void handleCallSignal(Message msg) {

        if (msg == null || msg.getType() == null) {
            return;
        }

        switch (msg.getType()) {

            case AUDIO_PORT_REGISTER:
                registerUdpPort(audioRelay, msg, "Audio");
                return;

            case VIDEO_PORT_REGISTER:
                registerUdpPort(videoRelay, msg, "Video");
                return;

            case CALL_ACCEPT:
                handleCallAccept(msg);
                return;

            case CALL_REJECT:
                forward(msg);
                rejectCall(msg);
                removePendingCall(msg);
                return;

            case CALL_END:
                forward(msg);
                endCall(msg);
                return;

            default:
                forward(msg);
        }
    }

    private void handleCallAccept(Message msg) {
        String acceptor = msg.getSender();
        String caller = msg.getReceiver();

        // Le contenu du CALL_ACCEPT contient normalement le port audio
        registerUdpPort(audioRelay, msg, "Audio");

        Integer callId = getCallId(caller, acceptor);
        if (callId != null) {
            callDAO.markAccepted(callId);
        }

        // Envoyer CALL_ACCEPT au caller
        forward(msg);

        CallRequest pending = pendingCalls.get(caller);

        CallRequest.CallType callType = CallRequest.CallType.AUDIO;

        if (pending != null) {
            callType = pending.getCallType();
        }

        CallSession session = new CallSession(caller, acceptor, callType);

        activeCalls.put(caller, session);
        activeCalls.put(acceptor, session);

        pendingCalls.remove(caller);

        System.out.println("[CallService] Session active : "
                + caller + " <-> " + acceptor
                + " type=" + callType);
    }

    private void rejectCall(Message msg) {
        Integer callId = getCallId(msg.getSender(), msg.getReceiver());
        if (callId != null) {
            callDAO.markRejected(callId);
        }
        if (msg.getSender() != null) activeCallIds.remove(msg.getSender());
        if (msg.getReceiver() != null) activeCallIds.remove(msg.getReceiver());
    }

    private void registerUdpPort(MediaRelay relay, Message msg, String kind) {
        String sender = msg.getSender();
        String content = msg.getContent();

        if (sender == null || content == null) {
            System.out.println("[CallService] " + kind + " register ignoré : sender/content null");
            return;
        }

        ClientHandler senderHandler = clients.get(sender);

        if (senderHandler == null) {
            System.out.println("[CallService] " + kind + " register impossible, client introuvable : " + sender);
            return;
        }

        try {
            int port = Integer.parseInt(content);

            if (port <= 0) {
                System.out.println("[CallService] Port " + kind + " invalide : " + port);
                return;
            }

            relay.registerPeer(
                    sender,
                    senderHandler.getClientAddress(),
                    port
            );

            System.out.println("[CallService] " + kind + " enregistré : "
                    + sender
                    + " IP=" + senderHandler.getClientAddress().getHostAddress()
                    + " port=" + port);

        } catch (NumberFormatException e) {
            System.out.println("[CallService] Port " + kind + " invalide pour "
                    + sender + " : " + content);
        }
    }

    private void forward(Message msg) {
        String receiver = msg.getReceiver();

        if (receiver == null) {
            return;
        }

        ClientHandler target = clients.get(receiver);

        if (target != null) {
            target.sendObject(msg);
        } else {
            System.out.println("[CallService] Forward impossible, receiver non connecté : " + receiver);
        }
    }

    private void removePendingCall(Message msg) {
        pendingCalls.remove(msg.getSender());
        pendingCalls.remove(msg.getReceiver());
    }

    private void endCall(Message msg) {
        String sender = msg.getSender();
        String receiver = msg.getReceiver();

        Integer callId = getCallId(sender, receiver);
        if (callId != null) {
            callDAO.endCall(callId, "ENDED");
        }

        if (sender != null) activeCallIds.remove(sender);
        if (receiver != null) activeCallIds.remove(receiver);

        if (sender != null) {
            audioRelay.unregisterPeer(sender);
            videoRelay.unregisterPeer(sender);
            pendingCalls.remove(sender);
            activeCalls.remove(sender);
        }

        if (receiver != null) {
            audioRelay.unregisterPeer(receiver);
            videoRelay.unregisterPeer(receiver);
            pendingCalls.remove(receiver);
            activeCalls.remove(receiver);
        }

        System.out.println("[CallService] Appel terminé : " + sender + " <-> " + receiver);
    }

    private Integer getCallId(String user1, String user2) {
        Integer callId = null;
        if (user1 != null) callId = activeCallIds.get(user1);
        if (callId == null && user2 != null) callId = activeCallIds.get(user2);
        return callId;
    }
}
