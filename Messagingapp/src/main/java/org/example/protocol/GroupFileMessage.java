package org.example.protocol;

import java.io.Serializable;

/**
 * Message fichier envoyé dans un groupe.
 * Étend FileMessage avec un groupId.
 */
public class GroupFileMessage implements Serializable {
    private static final long serialVersionUID = 10L;

    private String sender;
    private String groupId;
    private String fileName;
    private String fileType;
    private byte[] data;
    private String uploadedAt; // timestamp BDD pour tri chronologique (null = live)

    public GroupFileMessage(String sender, String groupId,
                            String fileName, String fileType, byte[] data) {
        this(sender, groupId, fileName, fileType, data, null);
    }

    public GroupFileMessage(String sender, String groupId,
                            String fileName, String fileType, byte[] data, String uploadedAt) {
        this.sender     = sender;
        this.groupId    = groupId;
        this.fileName   = fileName;
        this.fileType   = fileType;
        this.data       = data;
        this.uploadedAt = uploadedAt;
    }

    public String getSender()     { return sender; }
    public String getGroupId()    { return groupId; }
    public String getFileName()   { return fileName; }
    public String getFileType()   { return fileType; }
    public byte[] getData()       { return data; }
    public String getUploadedAt() { return uploadedAt; }
}
