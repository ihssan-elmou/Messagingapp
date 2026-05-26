package org.example.client.controller;

import org.example.client.network.RequestSender;
import org.example.protocol.*;

import java.util.List;

/**
 * GroupController V2 — toutes les actions groupe côté client.
 */
public class GroupController {

    private final String        username;
    private final RequestSender sender;

    public GroupController(String username, RequestSender sender) {
        this.username = username;
        this.sender   = sender;
    }

    /** Créer un groupe sans membres initiaux. */
    public void createGroup(String groupName) {
        sender.sendMessage(new Message(MessageType.GROUP_CREATE, username, null, groupName));
    }

    /** Créer un groupe avec une liste de membres initiaux. */
    public void createGroupWithMembers(String groupName, List<String> members) {
        // Format : "groupName|member1,member2,..."
        String content = members == null || members.isEmpty()
                ? groupName
                : groupName + "|" + String.join(",", members);
        sender.sendMessage(new Message(MessageType.GROUP_CREATE, username, null, content));
    }

    public void addMember(String groupId, String memberUsername) {
        sender.sendMessage(new Message(MessageType.GROUP_ADD_MEMBER,
                username, null, groupId + ":" + memberUsername));
    }

    public void removeMember(String groupId, String memberUsername) {
        sender.sendMessage(new Message(MessageType.GROUP_REMOVE_MEMBER,
                username, null, groupId + ":" + memberUsername));
    }

    public void sendGroupMessage(String groupId, String text) {
        Message msg = new Message(MessageType.GROUP_MESSAGE, username, null, text);
        msg.setGroupId(groupId);
        sender.sendMessage(msg);
    }

    /** Envoyer un fichier dans un groupe. */
    public void sendGroupFile(String groupId, String fileName, String fileType, byte[] data) {
        GroupFileMessage gfm = new GroupFileMessage(username, groupId, fileName, fileType, data);
        sender.sendRaw(gfm);
    }

    public void requestGroupList() {
        sender.sendMessage(new Message(MessageType.GROUP_LIST, username, null, ""));
    }
}
