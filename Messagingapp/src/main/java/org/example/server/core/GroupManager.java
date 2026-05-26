package org.example.server.core;

import org.example.model.Group;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GroupManager {

    private final Map<String, Group> groups = new ConcurrentHashMap<>();

    public Group createGroup(String name, String admin) {
        String id = UUID.randomUUID().toString();
        Group  g  = new Group(id, name, admin);
        groups.put(id, g);
        return g;
    }

    /**
     * Recrée un groupe depuis la BDD sans ajouter l'admin automatiquement.
     * Les membres sont ajoutés explicitement via addMember() depuis la BDD.
     */
    public Group createGroupFromDB(String id, String name, String admin) {
        Group g = new GroupNoBoot(id, name, admin);
        groups.put(id, g);
        return g;
    }

    public Group getGroup(String id)       { return groups.get(id); }
    public boolean exists(String id)       { return groups.containsKey(id); }
    public void    remove(String id)       { groups.remove(id); }
    public Collection<Group> getAll()      { return groups.values(); }

    /** Groupe chargé depuis BDD : membres gérés entièrement par addMember(). */
    private static class GroupNoBoot extends org.example.model.Group {
        GroupNoBoot(String id, String name, String admin) {
            super(id, name, admin);
            // Retirer l'admin ajouté automatiquement par le constructeur parent
            // Il sera ré-ajouté depuis la BDD via addMember()
            getMembers().clear();
        }
    }
}