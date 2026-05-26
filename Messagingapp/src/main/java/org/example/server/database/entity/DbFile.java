package org.example.server.database.entity;

public class DbFile {

    private int id;
    private String sender;
    private String receiver;
    private String groupId;
    private String fileName;
    private String fileType;
    private long fileSize;
    private String storagePath;
    private byte[] data;
    private String uploadedAt;

    public DbFile() {
    }

    public DbFile(String sender, String receiver, String fileName,
                  String fileType, long fileSize, byte[] data) {
        this.sender = sender;
        this.receiver = receiver;
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.data = data;
    }

    public DbFile(String sender, String receiver, String groupId,
                  String fileName, String fileType, long fileSize,
                  String storagePath, byte[] data) {
        this.sender = sender;
        this.receiver = receiver;
        this.groupId = groupId;
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.storagePath = storagePath;
        this.data = data;
    }

    public DbFile(int id, String sender, String receiver, String groupId,
                  String fileName, String fileType, long fileSize,
                  String storagePath, byte[] data, String uploadedAt) {
        this.id = id;
        this.sender = sender;
        this.receiver = receiver;
        this.groupId = groupId;
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.storagePath = storagePath;
        this.data = data;
        this.uploadedAt = uploadedAt;
    }

    public int getId() { return id; }
    public String getSender() { return sender; }
    public String getReceiver() { return receiver; }
    public String getGroupId() { return groupId; }
    public String getFileName() { return fileName; }
    public String getFileType() { return fileType; }
    public long getFileSize() { return fileSize; }
    public String getStoragePath() { return storagePath; }
    public byte[] getData() { return data; }
    public String getUploadedAt() { return uploadedAt; }

    public void setId(int id) { this.id = id; }
    public void setSender(String sender) { this.sender = sender; }
    public void setReceiver(String receiver) { this.receiver = receiver; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public void setData(byte[] data) { this.data = data; }
    public void setUploadedAt(String uploadedAt) { this.uploadedAt = uploadedAt; }
}
