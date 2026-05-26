package org.example.server.database.dao;

import org.example.server.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class GroupDAO {

    private final UserDAO userDAO = new UserDAO();

    /** Crée le groupe en BDD. L'admin doit être ajouté séparément via addMember(). */
    public boolean createGroup(String groupId, String name, String adminUsername) {
        userDAO.ensureUser(adminUsername);
        String sql = "INSERT IGNORE INTO chat_groups(id, name, admin_username) VALUES (?, ?, ?)";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, groupId);
            ps.setString(2, name);
            ps.setString(3, adminUsername);
            ps.executeUpdate();
            System.out.println("[GroupDAO] Groupe créé : " + name + " (" + groupId + ")");
            return true;
        } catch (Exception e) {
            System.out.println("[GroupDAO] Erreur createGroup : " + e.getMessage());
            return false;
        }
    }

    public boolean addMember(String groupId, String username, String role) {
        userDAO.ensureUser(username);
        String sql = "INSERT IGNORE INTO group_members(group_id, username, role) VALUES (?, ?, ?)";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, groupId);
            ps.setString(2, username);
            ps.setString(3, role != null ? role : "MEMBER");
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            System.out.println("[GroupDAO] Erreur addMember : " + e.getMessage());
            return false;
        }
    }

    public boolean removeMember(String groupId, String username) {
        String sql = "DELETE FROM group_members WHERE group_id = ? AND username = ?";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, groupId);
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            System.out.println("[GroupDAO] Erreur removeMember : " + e.getMessage());
            return false;
        }
    }

    public List<String> getMembers(String groupId) {
        List<String> members = new ArrayList<>();
        String sql = "SELECT username FROM group_members WHERE group_id = ? ORDER BY joined_at ASC";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, groupId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) members.add(rs.getString("username"));
        } catch (Exception e) {
            System.out.println("[GroupDAO] Erreur getMembers : " + e.getMessage());
        }
        return members;
    }

    public boolean isMember(String groupId, String username) {
        String sql = "SELECT 1 FROM group_members WHERE group_id = ? AND username = ?";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, groupId);
            ps.setString(2, username);
            return ps.executeQuery().next();
        } catch (Exception e) { return false; }
    }

    public List<GroupRow> getGroupsOfUser(String username) {
        List<GroupRow> groups = new ArrayList<>();
        String sql = """
                SELECT g.id, g.name, g.admin_username
                FROM chat_groups g
                JOIN group_members gm ON gm.group_id = g.id
                WHERE gm.username = ?
                ORDER BY g.created_at ASC
                """;
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                groups.add(new GroupRow(rs.getString("id"), rs.getString("name"),
                        rs.getString("admin_username")));
        } catch (Exception e) {
            System.out.println("[GroupDAO] Erreur getGroupsOfUser : " + e.getMessage());
        }
        return groups;
    }

    public GroupRow findById(String groupId) {
        String sql = "SELECT id, name, admin_username FROM chat_groups WHERE id = ?";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, groupId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return new GroupRow(rs.getString("id"),
                    rs.getString("name"), rs.getString("admin_username"));
        } catch (Exception e) { System.out.println("[GroupDAO] Erreur findById : " + e.getMessage()); }
        return null;
    }

    /** Retourne la date d'adhésion d'un membre dans un groupe (pour filtrer l'historique). */
    public String getJoinedAt(String groupId, String username) {
        String sql = "SELECT joined_at FROM group_members WHERE group_id = ? AND username = ?";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, groupId); ps.setString(2, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("joined_at");
        } catch (Exception e) { System.out.println("[GroupDAO] getJoinedAt: " + e.getMessage()); }
        return null;
    }

    public static class GroupRow {
        public final String id, name, admin;
        public GroupRow(String id, String name, String admin) {
            this.id = id; this.name = name; this.admin = admin;
        }
    }
}
