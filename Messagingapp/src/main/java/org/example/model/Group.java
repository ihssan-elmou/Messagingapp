package org.example.model;

import java.io.Serializable;
import java.util.*;

public class Group implements Serializable {
    private static final long serialVersionUID = 6L;

    private String       id;
    private String       name;
    private String       admin;
    private List<String> members = new ArrayList<>();

    public Group(String id, String name, String admin) {
        this.id    = id;
        this.name  = name;
        this.admin = admin;
        members.add(admin);
    }

    public String       getId()      { return id; }
    public String       getName()    { return name; }
    public String       getAdmin()   { return admin; }
    public List<String> getMembers() { return members; }

    public void addMember(String u)    { if (!members.contains(u)) members.add(u); }
    public void removeMember(String u) { members.remove(u); }
    public boolean hasMember(String u) { return members.contains(u); }
}