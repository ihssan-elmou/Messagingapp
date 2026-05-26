package org.example.server.database.dao;

import org.example.server.database.DatabaseManager;
import org.example.server.database.entity.DbUser;
import org.example.server.database.idao.IDAO;
import org.example.utils.PasswordUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class UserDAO implements IDAO<DbUser> {

    @Override
    public boolean create(DbUser user) {
        String sql = """
                INSERT INTO users(username, display_name, status)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    display_name = COALESCE(VALUES(display_name), display_name),
                    status = VALUES(status),
                    updated_at = CURRENT_TIMESTAMP
                """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user.getUsername());
            ps.setString(2, user.getDisplayName() != null ? user.getDisplayName() : user.getUsername());
            ps.setString(3, user.getStatus() != null ? user.getStatus() : "offline");
            ps.executeUpdate();
            return true;

        } catch (Exception e) {
            System.out.println("[UserDAO] Erreur create : " + e.getMessage());
            return false;
        }
    }

    public boolean ensureUser(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        return create(new DbUser(username.trim(), "offline"));
    }

    @Override
    public DbUser findById(int id) {
        String sql = "SELECT * FROM users WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return map(rs);

        } catch (Exception e) {
            System.out.println("[UserDAO] Erreur findById : " + e.getMessage());
        }

        return null;
    }

    public String getPasswordHash(String username) {
        String sql = "SELECT password_hash FROM users WHERE username = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("password_hash");
        } catch (Exception e) {
            System.out.println("[UserDAO] Erreur getPasswordHash : " + e.getMessage());
        }
        return null;
    }

    public boolean setPasswordHash(String username, String hash) {
        String sql = "UPDATE users SET password_hash = ?, updated_at = CURRENT_TIMESTAMP WHERE username = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hash);
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            System.out.println("[UserDAO] Erreur setPasswordHash : " + e.getMessage());
            return false;
        }
    }

    public DbUser findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return map(rs);

        } catch (Exception e) {
            System.out.println("[UserDAO] Erreur findByUsername : " + e.getMessage());
        }

        return null;
    }

    /** Comptes partageant le nom saisi (nom affiché ou identifiant interne). */
    public List<DbUser> findLoginCandidates(String loginName) {
        List<DbUser> users = new ArrayList<>();
        String sql = """
                SELECT * FROM users
                WHERE username = ? OR display_name = ?
                   OR username LIKE ?
                ORDER BY id ASC
                """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, loginName);
            ps.setString(2, loginName);
            ps.setString(3, loginName + "#%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) users.add(map(rs));

        } catch (Exception e) {
            System.out.println("[UserDAO] Erreur findLoginCandidates : " + e.getMessage());
        }

        return users;
    }

    public DbUser findByLoginNameAndPassword(String loginName, String password) {
        if (loginName == null || loginName.isBlank()) return null;
        String pwd = password != null ? password : "";
        for (DbUser u : findLoginCandidates(loginName.trim())) {
            String hash = getPasswordHash(u.getUsername());
            if (hash == null || hash.isBlank()) continue;
            if (PasswordUtils.verify(pwd, hash)) return u;
        }
        return null;
    }

    public boolean existsWithDisplayNameAndPassword(String displayName, String password) {
        return findByLoginNameAndPassword(displayName, password) != null;
    }

    /** Identifiant unique en base ; le nom affiché reste {@code displayName}. */
    public String allocateUsername(String displayName) {
        if (displayName == null || displayName.isBlank()) return displayName;
        if (findByUsername(displayName) == null) return displayName;
        int n = 2;
        while (findByUsername(displayName + "#" + n) != null) n++;
        return displayName + "#" + n;
    }

    public String getDisplayName(String username) {
        DbUser u = findByUsername(username);
        if (u == null) return username;
        String d = u.getDisplayName();
        return d != null && !d.isBlank() ? d : username;
    }

    @Override
    public List<DbUser> findAll() {
        List<DbUser> users = new ArrayList<>();
        String sql = "SELECT * FROM users ORDER BY username ASC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ResultSet rs = ps.executeQuery();
            while (rs.next()) users.add(map(rs));

        } catch (Exception e) {
            System.out.println("[UserDAO] Erreur findAll : " + e.getMessage());
        }

        return users;
    }

    @Override
    public boolean update(DbUser user) {
        String sql = """
                UPDATE users
                SET username = ?, display_name = ?, status = ?
                WHERE id = ?
                """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user.getUsername());
            ps.setString(2, user.getDisplayName());
            ps.setString(3, user.getStatus());
            ps.setInt(4, user.getId());
            return ps.executeUpdate() > 0;

        } catch (Exception e) {
            System.out.println("[UserDAO] Erreur update : " + e.getMessage());
            return false;
        }
    }

    public boolean updateStatus(String username, String status) {
        String sql = """
                UPDATE users
                SET status = ?,
                    last_seen = CASE WHEN ? = 'offline' THEN CURRENT_TIMESTAMP ELSE last_seen END
                WHERE username = ?
                """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setString(2, status);
            ps.setString(3, username);
            return ps.executeUpdate() > 0;

        } catch (Exception e) {
            System.out.println("[UserDAO] Erreur updateStatus : " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean delete(int id) {
        String sql = "DELETE FROM users WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            return ps.executeUpdate() > 0;

        } catch (Exception e) {
            System.out.println("[UserDAO] Erreur delete : " + e.getMessage());
            return false;
        }
    }

    private DbUser map(ResultSet rs) throws Exception {
        return new DbUser(
                rs.getInt("id"),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("status"),
                rs.getString("last_seen"),
                rs.getString("created_at"),
                rs.getString("updated_at")
        );
    }
}
