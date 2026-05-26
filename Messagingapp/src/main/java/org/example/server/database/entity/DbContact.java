package org.example.server.database.entity;

public class DbContact {

    private int id;
    private String ownerUsername;
    private String contactUsername;
    private Integer lastMessageId;
    private Integer lastCallId;
    private String lastInteractionAt;
    private String createdAt;

    public DbContact() {
    }

    public DbContact(String ownerUsername, String contactUsername) {
        this.ownerUsername = ownerUsername;
        this.contactUsername = contactUsername;
    }

    public DbContact(int id, String ownerUsername, String contactUsername,
                     Integer lastMessageId, Integer lastCallId,
                     String lastInteractionAt, String createdAt) {
        this.id = id;
        this.ownerUsername = ownerUsername;
        this.contactUsername = contactUsername;
        this.lastMessageId = lastMessageId;
        this.lastCallId = lastCallId;
        this.lastInteractionAt = lastInteractionAt;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public String getOwnerUsername() { return ownerUsername; }
    public String getContactUsername() { return contactUsername; }
    public Integer getLastMessageId() { return lastMessageId; }
    public Integer getLastCallId() { return lastCallId; }
    public String getLastInteractionAt() { return lastInteractionAt; }
    public String getCreatedAt() { return createdAt; }

    public void setId(int id) { this.id = id; }
    public void setOwnerUsername(String ownerUsername) { this.ownerUsername = ownerUsername; }
    public void setContactUsername(String contactUsername) { this.contactUsername = contactUsername; }
    public void setLastMessageId(Integer lastMessageId) { this.lastMessageId = lastMessageId; }
    public void setLastCallId(Integer lastCallId) { this.lastCallId = lastCallId; }
    public void setLastInteractionAt(String lastInteractionAt) { this.lastInteractionAt = lastInteractionAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
