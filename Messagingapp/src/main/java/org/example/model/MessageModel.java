package org.example.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MessageModel {
    private String        sender;
    private String        content;
    private LocalDateTime time;
    private boolean       fromMe;

    public MessageModel(String sender, String content, boolean fromMe) {
        this.sender  = sender;
        this.content = content;
        this.time    = LocalDateTime.now();
        this.fromMe  = fromMe;
    }

    public String  getSender()  { return sender; }
    public String  getContent() { return content; }
    public boolean isFromMe()   { return fromMe; }

    public String getFormattedTime() {
        return time.format(DateTimeFormatter.ofPattern("HH:mm"));
    }
}