package org.example.model;

import org.example.protocol.Status;
import java.io.Serializable;

public class User implements Serializable {
    private static final long serialVersionUID = 3L;

    private String username;
    private Status status;

    public User(String username) {
        this.username = username;
        this.status   = Status.ONLINE;
    }

    public String getUsername()              { return username; }
    public Status getStatus()                { return status; }
    public void   setStatus(Status status)   { this.status = status; }
    public boolean isOnline() {
        return status == Status.ONLINE || status == Status.IN_CALL;
    }

    @Override
    public String toString() { return username; }
}