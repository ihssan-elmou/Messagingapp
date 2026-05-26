package org.example.server.database.dao;

import org.example.server.database.DatabaseManager;
import org.example.server.database.entity.DbMessage;
import org.example.server.database.idao.IDAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class MessageDAO implements IDAO<DbMessage> {

    private final UserDAO userDAO = new UserDAO();
    private final ContactDAO contactDAO = new ContactDAO();

    @Override
    public boolean create(DbMessage message) {
        int id = createAndReturnId(message);
        return id > 0;
    }

    public int createAndReturnId(DbMessage message) {
        userDAO.ensureUser(message.getSender());
        if (message.getReceiver() != null) userDAO.ensureUser(message.getReceiver());

        String sql = """
                INSERT INTO messages(sender, receiver, group_id, content, message_type, file_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, message.getSender());
            ps.setString(2, message.getReceiver());
            ps.setString(3, message.getGroupId());
            ps.setString(4, message.getContent());
            ps.setString(5, message.getMessageType() != null ? message.getMessageType() : "TEXT");
            if (message.getFileId() == null) ps.setNull(6, Types.INTEGER);
            else ps.setInt(6, message.getFileId());

            ps.executeUpdate();

            int generatedId = -1;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) generatedId = keys.getInt(1);
            }

            if (generatedId > 0) {
                message.setId(generatedId);
                if (message.getReceiver() != null) {
                    contactDAO.touchBothContacts(
                            message.getSender(),
                            message.getReceiver(),
                            generatedId,
                            null
                    );
                }
            }

            System.out.println("[MessageDAO] Message sauvegardé : "
                    + message.getSender() + " -> " + message.getReceiver());

            return generatedId;

        } catch (Exception e) {
            System.out.println("[MessageDAO] Erreur createAndReturnId : " + e.getMessage());
            return -1;
        }
    }

    @Override
    public DbMessage findById(int id) {
        String sql = "SELECT * FROM messages WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return map(rs);

        } catch (Exception e) {
            System.out.println("[MessageDAO] Erreur findById : " + e.getMessage());
        }

        return null;
    }

    @Override
    public List<DbMessage> findAll() {
        List<DbMessage> messages = new ArrayList<>();
        String sql = "SELECT * FROM messages ORDER BY sent_at ASC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ResultSet rs = ps.executeQuery();
            while (rs.next()) messages.add(map(rs));

        } catch (Exception e) {
            System.out.println("[MessageDAO] Erreur findAll : " + e.getMessage());
        }

        return messages;
    }

    public List<DbMessage> findConversation(String user1, String user2) {
        List<DbMessage> messages = new ArrayList<>();
        String sql = """
                SELECT * FROM messages
                WHERE group_id IS NULL
                  AND ((sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?))
                ORDER BY sent_at ASC
                """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user1);
            ps.setString(2, user2);
            ps.setString(3, user2);
            ps.setString(4, user1);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) messages.add(map(rs));

        } catch (Exception e) {
            System.out.println("[MessageDAO] Erreur findConversation : " + e.getMessage());
        }

        return messages;
    }

    public List<DbMessage> findGroupMessages(String groupId) {
        return findGroupMessages(groupId, null);
    }

    /**
     * Retourne les messages d'un groupe depuis une date donnée (pour les nouveaux membres).
     * Si since est null, retourne tous les messages.
     */
    public List<DbMessage> findGroupMessages(String groupId, String since) {
        List<DbMessage> messages = new ArrayList<>();
        String sql = since != null
            ? "SELECT * FROM messages WHERE group_id = ? AND sent_at >= ? ORDER BY sent_at ASC"
            : "SELECT * FROM messages WHERE group_id = ? ORDER BY sent_at ASC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, groupId);
            if (since != null) ps.setString(2, since);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) messages.add(map(rs));

        } catch (Exception e) {
            System.out.println("[MessageDAO] Erreur findGroupMessages : " + e.getMessage());
        }

        return messages;
    }

    public boolean markDelivered(int id) {
        return updateTimestamp(id, "delivered_at");
    }

    public boolean markSeen(int id) {
        return updateTimestamp(id, "seen_at");
    }

    public int countUnreadFrom(String owner, String contact) {
        String sql = """
                SELECT COUNT(*) AS n FROM messages
                WHERE group_id IS NULL
                  AND receiver = ? AND sender = ?
                  AND seen_at IS NULL
                """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, owner);
            ps.setString(2, contact);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("n");
        } catch (Exception e) {
            System.out.println("[MessageDAO] Erreur countUnreadFrom : " + e.getMessage());
        }
        return 0;
    }

    public boolean markConversationSeen(String owner, String contact) {
        String sql = """
                UPDATE messages
                SET seen_at = CURRENT_TIMESTAMP
                WHERE group_id IS NULL
                  AND receiver = ? AND sender = ?
                  AND seen_at IS NULL
                """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, owner);
            ps.setString(2, contact);
            return ps.executeUpdate() >= 0;
        } catch (Exception e) {
            System.out.println("[MessageDAO] Erreur markConversationSeen : " + e.getMessage());
            return false;
        }
    }

    public DbMessage findLastGroupMessage(String groupId) {
        String sql = """
                SELECT * FROM messages
                WHERE group_id = ?
                ORDER BY sent_at DESC
                LIMIT 1
                """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return map(rs);
        } catch (Exception e) {
            System.out.println("[MessageDAO] Erreur findLastGroupMessage : " + e.getMessage());
        }
        return null;
    }

    public DbMessage findLastPrivateMessage(String user1, String user2) {
        String sql = """
                SELECT * FROM messages
                WHERE group_id IS NULL
                  AND ((sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?))
                ORDER BY sent_at DESC
                LIMIT 1
                """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user1);
            ps.setString(2, user2);
            ps.setString(3, user2);
            ps.setString(4, user1);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return map(rs);
        } catch (Exception e) {
            System.out.println("[MessageDAO] Erreur findLastPrivateMessage : " + e.getMessage());
        }
        return null;
    }

    private boolean updateTimestamp(int id, String column) {
        String sql = "UPDATE messages SET " + column + " = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            return ps.executeUpdate() > 0;

        } catch (Exception e) {
            System.out.println("[MessageDAO] Erreur updateTimestamp : " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean update(DbMessage message) {
        String sql = """
                UPDATE messages
                SET content = ?, message_type = ?, file_id = ?
                WHERE id = ?
                """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, message.getContent());
            ps.setString(2, message.getMessageType() != null ? message.getMessageType() : "TEXT");
            if (message.getFileId() == null) ps.setNull(3, Types.INTEGER);
            else ps.setInt(3, message.getFileId());
            ps.setInt(4, message.getId());
            return ps.executeUpdate() > 0;

        } catch (Exception e) {
            System.out.println("[MessageDAO] Erreur update : " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean delete(int id) {
        String sql = "DELETE FROM messages WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            return ps.executeUpdate() > 0;

        } catch (Exception e) {
            System.out.println("[MessageDAO] Erreur delete : " + e.getMessage());
            return false;
        }
    }

    private DbMessage map(ResultSet rs) throws Exception {
        Integer fileId = rs.getObject("file_id") == null ? null : rs.getInt("file_id");

        return new DbMessage(
                rs.getInt("id"),
                rs.getString("sender"),
                rs.getString("receiver"),
                rs.getString("group_id"),
                rs.getString("content"),
                rs.getString("message_type"),
                fileId,
                rs.getString("sent_at"),
                rs.getString("delivered_at"),
                rs.getString("seen_at")
        );
    }
}
