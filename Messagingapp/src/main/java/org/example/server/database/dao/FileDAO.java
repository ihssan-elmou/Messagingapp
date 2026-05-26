package org.example.server.database.dao;

import org.example.server.database.DatabaseManager;
import org.example.server.database.entity.DbFile;
import org.example.server.database.idao.IDAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class FileDAO implements IDAO<DbFile> {

    private final UserDAO userDAO = new UserDAO();
    private final ContactDAO contactDAO = new ContactDAO();

    @Override
    public boolean create(DbFile file) {
        int id = createAndReturnId(file);
        return id > 0;
    }

    public int createAndReturnId(DbFile file) {
        userDAO.ensureUser(file.getSender());
        if (file.getReceiver() != null) userDAO.ensureUser(file.getReceiver());

        String sql = """
                INSERT INTO files(sender, receiver, group_id, file_name, file_type, file_size, storage_path, data)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, file.getSender());
            ps.setString(2, file.getReceiver());
            ps.setString(3, file.getGroupId());
            ps.setString(4, file.getFileName());
            ps.setString(5, file.getFileType());
            ps.setLong(6, file.getFileSize());
            ps.setString(7, file.getStoragePath());
            if (file.getData() == null) ps.setNull(8, Types.BLOB);
            else ps.setBytes(8, file.getData());

            ps.executeUpdate();

            int generatedId = -1;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) generatedId = keys.getInt(1);
            }

            if (generatedId > 0) {
                file.setId(generatedId);
                if (file.getReceiver() != null) {
                    contactDAO.touchBothContacts(file.getSender(), file.getReceiver(), null, null);
                }
            }

            System.out.println("[FileDAO] Fichier sauvegardé : " + file.getFileName());
            return generatedId;

        } catch (Exception e) {
            System.out.println("[FileDAO] Erreur createAndReturnId : " + e.getMessage());
            return -1;
        }
    }

    @Override
    public DbFile findById(int id) {
        String sql = "SELECT * FROM files WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return map(rs);

        } catch (Exception e) {
            System.out.println("[FileDAO] Erreur findById : " + e.getMessage());
        }

        return null;
    }

    @Override
    public List<DbFile> findAll() {
        List<DbFile> files = new ArrayList<>();
        String sql = "SELECT * FROM files ORDER BY uploaded_at ASC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ResultSet rs = ps.executeQuery();
            while (rs.next()) files.add(map(rs));

        } catch (Exception e) {
            System.out.println("[FileDAO] Erreur findAll : " + e.getMessage());
        }

        return files;
    }

    public List<DbFile> findFilesBetween(String user1, String user2) {
        List<DbFile> files = new ArrayList<>();
        String sql = """
                SELECT * FROM files
                WHERE group_id IS NULL
                  AND ((sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?))
                ORDER BY uploaded_at ASC
                """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user1);
            ps.setString(2, user2);
            ps.setString(3, user2);
            ps.setString(4, user1);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) files.add(map(rs));

        } catch (Exception e) {
            System.out.println("[FileDAO] Erreur findFilesBetween : " + e.getMessage());
        }

        return files;
    }

    public DbFile findLastFileBetween(String user1, String user2) {
        String sql = """
                SELECT * FROM files
                WHERE group_id IS NULL
                  AND ((sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?))
                ORDER BY uploaded_at DESC
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
            System.out.println("[FileDAO] Erreur findLastFileBetween : " + e.getMessage());
        }
        return null;
    }

    /** Trouve un fichier de groupe par expéditeur + nom (pour l'historique ordonné). */
    public DbFile findGroupFile(String groupId, String sender, String fileName) {
        String sql = """
                SELECT * FROM files
                WHERE group_id = ? AND sender = ? AND file_name = ?
                ORDER BY uploaded_at DESC
                LIMIT 1
                """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupId);
            ps.setString(2, sender);
            ps.setString(3, fileName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return map(rs);
        } catch (Exception e) {
            System.out.println("[FileDAO] Erreur findGroupFile : " + e.getMessage());
        }
        return null;
    }

    public List<DbFile> findGroupFiles(String groupId) {
        List<DbFile> files = new ArrayList<>();
        String sql = """
                SELECT * FROM files
                WHERE group_id = ?
                ORDER BY uploaded_at ASC
                """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, groupId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) files.add(map(rs));

        } catch (Exception e) {
            System.out.println("[FileDAO] Erreur findGroupFiles : " + e.getMessage());
        }

        return files;
    }

    @Override
    public boolean update(DbFile file) {
        String sql = """
                UPDATE files
                SET file_name = ?, file_type = ?, file_size = ?, storage_path = ?, data = ?
                WHERE id = ?
                """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, file.getFileName());
            ps.setString(2, file.getFileType());
            ps.setLong(3, file.getFileSize());
            ps.setString(4, file.getStoragePath());
            if (file.getData() == null) ps.setNull(5, Types.BLOB);
            else ps.setBytes(5, file.getData());
            ps.setInt(6, file.getId());
            return ps.executeUpdate() > 0;

        } catch (Exception e) {
            System.out.println("[FileDAO] Erreur update : " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean delete(int id) {
        String sql = "DELETE FROM files WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            return ps.executeUpdate() > 0;

        } catch (Exception e) {
            System.out.println("[FileDAO] Erreur delete : " + e.getMessage());
            return false;
        }
    }

    private DbFile map(ResultSet rs) throws Exception {
        return new DbFile(
                rs.getInt("id"),
                rs.getString("sender"),
                rs.getString("receiver"),
                rs.getString("group_id"),
                rs.getString("file_name"),
                rs.getString("file_type"),
                rs.getLong("file_size"),
                rs.getString("storage_path"),
                rs.getBytes("data"),
                rs.getString("uploaded_at")
        );
    }
}
