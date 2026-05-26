package org.example.server.database.entity;

public class DbCall {

    private int id;
    private String caller;
    private String callee;
    private String groupId;
    private String callType;
    private String callStatus;
    private String startedAt;
    private String acceptedAt;
    private String endedAt;
    private int durationSeconds;
    private String endReason;

    public DbCall() {
    }

    public DbCall(String caller, String callee, String callType, String callStatus) {
        this.caller = caller;
        this.callee = callee;
        this.callType = callType;
        this.callStatus = callStatus;
    }

    public DbCall(String caller, String callee, String groupId,
                  String callType, String callStatus) {
        this.caller = caller;
        this.callee = callee;
        this.groupId = groupId;
        this.callType = callType;
        this.callStatus = callStatus;
    }

    public DbCall(int id, String caller, String callee, String groupId,
                  String callType, String callStatus, String startedAt,
                  String acceptedAt, String endedAt, int durationSeconds,
                  String endReason) {
        this.id = id;
        this.caller = caller;
        this.callee = callee;
        this.groupId = groupId;
        this.callType = callType;
        this.callStatus = callStatus;
        this.startedAt = startedAt;
        this.acceptedAt = acceptedAt;
        this.endedAt = endedAt;
        this.durationSeconds = durationSeconds;
        this.endReason = endReason;
    }

    public int getId() { return id; }
    public String getCaller() { return caller; }
    public String getCallee() { return callee; }
    public String getGroupId() { return groupId; }
    public String getCallType() { return callType; }
    public String getCallStatus() { return callStatus; }
    public String getStartedAt() { return startedAt; }
    public String getAcceptedAt() { return acceptedAt; }
    public String getEndedAt() { return endedAt; }
    public int getDurationSeconds() { return durationSeconds; }
    public String getEndReason() { return endReason; }

    public void setId(int id) { this.id = id; }
    public void setCaller(String caller) { this.caller = caller; }
    public void setCallee(String callee) { this.callee = callee; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public void setCallType(String callType) { this.callType = callType; }
    public void setCallStatus(String callStatus) { this.callStatus = callStatus; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }
    public void setAcceptedAt(String acceptedAt) { this.acceptedAt = acceptedAt; }
    public void setEndedAt(String endedAt) { this.endedAt = endedAt; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }
    public void setEndReason(String endReason) { this.endReason = endReason; }
}
