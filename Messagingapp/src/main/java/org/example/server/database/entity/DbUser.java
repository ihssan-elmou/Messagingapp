package org.example.server.database.entity;

public class DbUser {

    private int id;
    private String username;
    private String displayName;
    private String status;
    private String lastSeen;
    private String createdAt;
    private String updatedAt;

    public DbUser() {
    }

    public DbUser(String username, String status) {
        this.username = username;
        this.displayName = username;
        this.status = status;
    }

    public DbUser(String username, String displayName, String status) {
        this.username = username;
        this.displayName = displayName != null ? displayName : username;
        this.status = status;
    }

    public DbUser(int id, String username, String status, String createdAt) {
        this.id = id;
        this.username = username;
        this.displayName = username;
        this.status = status;
        this.createdAt = createdAt;
    }

    public DbUser(int id, String username, String displayName, String status,
                  String lastSeen, String createdAt, String updatedAt) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.status = status;
        this.lastSeen = lastSeen;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getStatus() { return status; }
    public String getLastSeen() { return lastSeen; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }

    public void setId(int id) { this.id = id; }
    public void setUsername(String username) { this.username = username; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setStatus(String status) { this.status = status; }
    public void setLastSeen(String lastSeen) { this.lastSeen = lastSeen; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
