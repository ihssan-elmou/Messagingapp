package org.example.server.database.entity;

public class DbMessage {

    private int id;
    private String sender;
    private String receiver;
    private String groupId;
    private String content;
    private String messageType;
    private Integer fileId;
    private String sentAt;
    private String deliveredAt;
    private String seenAt;

    public DbMessage() {
    }

    public DbMessage(String sender, String receiver, String content) {
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.messageType = "TEXT";
    }

    public DbMessage(String sender, String receiver, String groupId,
                     String content, String messageType, Integer fileId) {
        this.sender = sender;
        this.receiver = receiver;
        this.groupId = groupId;
        this.content = content;
        this.messageType = messageType;
        this.fileId = fileId;
    }

    public DbMessage(int id, String sender, String receiver, String content, String sentAt) {
        this.id = id;
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.messageType = "TEXT";
        this.sentAt = sentAt;
    }

    public DbMessage(int id, String sender, String receiver, String groupId,
                     String content, String messageType, Integer fileId,
                     String sentAt, String deliveredAt, String seenAt) {
        this.id = id;
        this.sender = sender;
        this.receiver = receiver;
        this.groupId = groupId;
        this.content = content;
        this.messageType = messageType;
        this.fileId = fileId;
        this.sentAt = sentAt;
        this.deliveredAt = deliveredAt;
        this.seenAt = seenAt;
    }

    public int getId() { return id; }
    public String getSender() { return sender; }
    public String getReceiver() { return receiver; }
    public String getGroupId() { return groupId; }
    public String getContent() { return content; }
    public String getMessageType() { return messageType; }
    public Integer getFileId() { return fileId; }
    public String getSentAt() { return sentAt; }
    public String getDeliveredAt() { return deliveredAt; }
    public String getSeenAt() { return seenAt; }

    public void setId(int id) { this.id = id; }
    public void setSender(String sender) { this.sender = sender; }
    public void setReceiver(String receiver) { this.receiver = receiver; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public void setContent(String content) { this.content = content; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
    public void setFileId(Integer fileId) { this.fileId = fileId; }
    public void setSentAt(String sentAt) { this.sentAt = sentAt; }
    public void setDeliveredAt(String deliveredAt) { this.deliveredAt = deliveredAt; }
    public void setSeenAt(String seenAt) { this.seenAt = seenAt; }
}
