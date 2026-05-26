package org.example.model;

import java.time.LocalDateTime;

public class CallSession {
    private String              participant1;
    private String              participant2;
    private CallRequest.CallType callType;
    private LocalDateTime       startTime;
    private boolean             active;

    public CallSession(String p1, String p2, CallRequest.CallType type) {
        this.participant1 = p1;
        this.participant2 = p2;
        this.callType     = type;
        this.startTime    = LocalDateTime.now();
        this.active       = true;
    }

    public String               getParticipant1() { return participant1; }
    public String               getParticipant2() { return participant2; }
    public CallRequest.CallType getCallType()     { return callType; }
    public boolean              isActive()        { return active; }
    public void                 setActive(boolean a) { this.active = a; }
    public LocalDateTime        getStartTime()    { return startTime; }
}