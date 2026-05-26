package org.example.protocol;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    private MessageType   type;
    private String        sender;
    private String        receiver;   // null = broadcast
    private String        content;
    private String        groupId;    // utilisé pour les messages de groupe
    private LocalDateTime timestamp;

    public Message(MessageType type, String sender, String receiver, String content) {
        this.type      = type;
        this.sender    = sender;
        this.receiver  = receiver;
        this.content   = content;
        this.timestamp = LocalDateTime.now();
    }

    // Getter / Setter
    public MessageType   getType()      { return type; }
    public String        getSender()    { return sender; }
    public String        getReceiver()  { return receiver; }
    public String        getContent()   { return content; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String        getGroupId()   { return groupId; }
    public void          setGroupId(String groupId) { this.groupId = groupId; }
    public void          setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return "[" + type + "] " + sender + " -> "
                + (receiver != null ? receiver : "ALL")
                + " : " + content;
    }
}
