package org.example.client.network;

import org.example.model.CallRequest;
import org.example.protocol.*;

/**
 * Interface de callback : le ClientSocketManager notifie via cette interface.
 * Implémentée par les Controllers.
 */
public interface ClientListener {
    void onMessage(Message msg);
    void onCallRequest(CallRequest req);
    void onFileReceived(FileMessage fm);
    void onDisconnected();
}