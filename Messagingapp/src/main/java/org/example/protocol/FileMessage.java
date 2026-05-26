package org.example.protocol;
import java.io.Serializable;
public class FileMessage implements Serializable {
    private static final long serialVersionUID = 3L;

    private String sender;
    private String receiver;
    private String fileName;
    private String fileType;
    private long   fileSize;
    private byte[] data;
    private String uploadedAt; // timestamp BDD pour tri chronologique (null = live)

    /** Constructeur live */
    public FileMessage(String sender, String receiver,
                       String fileName, String fileType, byte[] data) {
        this.sender     = sender;
        this.receiver   = receiver;
        this.fileName   = fileName;
        this.fileType   = fileType;
        this.fileSize   = data != null ? data.length : 0;
        this.data       = data;
        this.uploadedAt = null;
    }

    /** Constructeur historique (depuis BDD) */
    public FileMessage(String sender, String receiver,
                       String fileName, String fileType, byte[] data, String uploadedAt) {
        this(sender, receiver, fileName, fileType, data);
        this.uploadedAt = uploadedAt;
    }

    public String getSender()     { return sender; }
    public String getReceiver()   { return receiver; }
    public String getFileName()   { return fileName; }
    public String getFileType()   { return fileType; }
    public long   getFileSize()   { return fileSize; }
    public byte[] getData()       { return data; }
    public String getUploadedAt() { return uploadedAt; }
}
