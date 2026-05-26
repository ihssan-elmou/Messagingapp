package org.example.model;

import java.io.Serializable;

public class CallRequest implements Serializable {
    private static final long serialVersionUID = 4L;

    public enum CallType { AUDIO, VIDEO }

    private String   caller;
    private String   callee;
    private CallType callType;
    private int      audioPort;
    private int      audioListenPort;  // ← renommé de videoPort

    public CallRequest(String caller, String callee,
                       CallType callType, int audioPort, int audioListenPort) {
        this.caller          = caller;
        this.callee          = callee;
        this.callType        = callType;
        this.audioPort       = audioPort;
        this.audioListenPort = audioListenPort;  // ← maintenant sauvegardé
    }

    public String   getCaller()               { return caller; }
    public String   getCallee()               { return callee; }
    public CallType getCallType()             { return callType; }
    public int      getAudioPort()            { return audioPort; }
    public int      getAudioListenPort()      { return audioListenPort; }
    public void     setAudioListenPort(int p) { this.audioListenPort = p; }
}