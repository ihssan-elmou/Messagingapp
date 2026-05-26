package org.example.model;

import java.io.Serializable;
import java.util.*;

public class MeetingSession implements Serializable {
    private static final long serialVersionUID = 7L;

    private String       id;
    private String       groupId;
    private String       meetingType = "AUDIO";
    private String       startedBy;
    private List<String> participants = new ArrayList<>();
    private boolean      active;

    public MeetingSession(String id, String groupId, String meetingType, String startedBy) {
        this.id          = id;
        this.groupId     = groupId;
        this.meetingType = meetingType != null ? meetingType : "AUDIO";
        this.startedBy   = startedBy;
        this.active      = true;
    }

    public String       getId()           { return id; }
    public String       getGroupId()      { return groupId; }
    public String       getMeetingType()  { return meetingType; }
    public String       getStartedBy()    { return startedBy; }
    public boolean      isActive()        { return active; }
    public List<String> getParticipants() { return participants; }

    public void addParticipant(String u)    { participants.add(u); }
    public void removeParticipant(String u) { participants.remove(u); }
    public void setActive(boolean active)   { this.active = active; }
}