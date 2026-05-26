package org.example.server.database.dao;

import org.example.server.database.DatabaseManager;
import org.example.server.database.entity.DbContact;
import org.example.server.database.idao.IDAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class ContactDAO implements IDAO<DbContact> {

    private final UserDAO userDAO = new UserDAO();

    @Override
    public boolean create(DbContact contact) {
        return touchContact(
                contact.getOwnerUsername(),
                contact.getContactUsername(),
                contact.getLastMessageId(),
                contact.getLastCallId()
        );
    }

    public boolean touchContact(String ownerUsername, String contactUsername) {
        return touchContact(ownerUsername, contactUsername, null, null);
    }

    public boolean touchContact(String ownerUsername, String contactUsername,
                                Integer lastMessageId, Integer lastCallId) {
        if (ownerUsername == null || contactUsername == null || ownerUsername.equals(contactUsername)) {
            return false;
        }

        userDAO.ensureUser(ownerUsername);
        userDAO.ensureUser(contactUsername);

        String sql = """
                INSERT INTO contacts(owner_username, contact_username, last_message_id, last_call_id, last_interaction_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE
                    last_message_id = COALESCE(VALUES(last_message_id), last_message_id),
                    last_call_id = COALESCE(VALUES(last_call_id), last_call_id),
                    last_interaction_at = CURRENT_TIMESTAMP
                """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, ownerUsername);
            ps.setString(2, contactUsername);
            setNullableInt(ps, 3, lastMessageId);
            setNullableInt(ps, 4, lastCallId);
            ps.executeUpdate();
            return true;

        } catch (Exception e) {
            System.out.println("[ContactDAO] Erreur touchContact : " + e.getMessage());
            return false;
        }
    }

    public boolean touchBothContacts(String user1, String user2, Integer lastMessageId, Integer lastCallId) {
        boolean a = touchContact(user1, user2, lastMessageId, lastCallId);
        boolean b = touchContact(user2, user1, lastMessageId, lastCallId);
        return a && b;
    }

    @Override
    public DbContact findById(int id) {
        String sql = "SELECT * FROM contacts WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return map(rs);

        } catch (Exception e) {
            System.out.println("[ContactDAO] Erreur findById : " + e.getMessage());
        }

        return null;
    }

    public List<DbContact> findContactsOf(String ownerUsername) {
        List<DbContact> contacts = new ArrayList<>();
        String sql = """
                SELECT * FROM contacts
                WHERE owner_username = ?
                ORDER BY last_interaction_at DESC
                """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, ownerUsername);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) contacts.add(map(rs));

        } catch (Exception e) {
            System.out.println("[ContactDAO] Erreur findContactsOf : " + e.getMessage());
        }

        return contacts;
    }

    @Override
    public List<DbContact> findAll() {
        List<DbContact> contacts = new ArrayList<>();
        String sql = "SELECT * FROM contacts ORDER BY owner_username ASC, last_interaction_at DESC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ResultSet rs = ps.executeQuery();
            while (rs.next()) contacts.add(map(rs));

        } catch (Exception e) {
            System.out.println("[ContactDAO] Erreur findAll : " + e.getMessage());
        }

        return contacts;
    }

    @Override
    public boolean update(DbContact contact) {
        String sql = """
                UPDATE contacts
                SET last_message_id = ?, last_call_id = ?, last_interaction_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            setNullableInt(ps, 1, contact.getLastMessageId());
            setNullableInt(ps, 2, contact.getLastCallId());
            ps.setInt(3, contact.getId());
            return ps.executeUpdate() > 0;

        } catch (Exception e) {
            System.out.println("[ContactDAO] Erreur update : " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean delete(int id) {
        String sql = "DELETE FROM contacts WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            return ps.executeUpdate() > 0;

        } catch (Exception e) {
            System.out.println("[ContactDAO] Erreur delete : " + e.getMessage());
            return false;
        }
    }

    private void setNullableInt(PreparedStatement ps, int index, Integer value) throws Exception {
        if (value == null) ps.setNull(index, Types.INTEGER);
        else ps.setInt(index, value);
    }

    private DbContact map(ResultSet rs) throws Exception {
        Integer lastMessageId = rs.getObject("last_message_id") == null ? null : rs.getInt("last_message_id");
        Integer lastCallId = rs.getObject("last_call_id") == null ? null : rs.getInt("last_call_id");

        return new DbContact(
                rs.getInt("id"),
                rs.getString("owner_username"),
                rs.getString("contact_username"),
                lastMessageId,
                lastCallId,
                rs.getString("last_interaction_at"),
                rs.getString("created_at")
        );
    }
}
