package org.example.server.database.dao;

import org.example.server.database.DatabaseManager;
import org.example.server.database.entity.DbCall;
import org.example.server.database.idao.IDAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class CallDAO implements IDAO<DbCall> {

    private final UserDAO userDAO = new UserDAO();
    private final ContactDAO contactDAO = new ContactDAO();

    @Override
    public boolean create(DbCall call) {
        int id = createAndReturnId(call);
        return id > 0;
    }

    public int createAndReturnId(DbCall call) {
        userDAO.ensureUser(call.getCaller());
        if (call.getCallee() != null) userDAO.ensureUser(call.getCallee());

        String sql = """
                INSERT INTO calls(caller, callee, group_id, call_type, call_status)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, call.getCaller());
            ps.setString(2, call.getCallee());
            ps.setString(3, call.getGroupId());
            ps.setString(4, call.getCallType() != null ? call.getCallType() : "AUDIO");
            ps.setString(5, call.getCallStatus() != null ? call.getCallStatus() : "PENDING");

            ps.executeUpdate();

            int generatedId = -1;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) generatedId = keys.getInt(1);
            }

            if (generatedId > 0) {
                call.setId(generatedId);
                if (call.getCallee() != null) {
                    contactDAO.touchBothContacts(call.getCaller(), call.getCallee(), null, generatedId);
                }
            }

            System.out.println("[CallDAO] Appel sauvegardé : "
                    + call.getCaller() + " -> " + call.getCallee()
                    + " type=" + call.getCallType());

            return generatedId;

        } catch (Exception e) {
            System.out.println("[CallDAO] Erreur createAndReturnId : " + e.getMessage());
            return -1;
        }
    }

    @Override
    public DbCall findById(int id) {
        String sql = "SELECT * FROM calls WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return map(rs);

        } catch (Exception e) {
            System.out.println("[CallDAO] Erreur findById : " + e.getMessage());
        }

        return null;
    }

    @Override
    public List<DbCall> findAll() {
        List<DbCall> calls = new ArrayList<>();
        String sql = "SELECT * FROM calls ORDER BY started_at DESC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ResultSet rs = ps.executeQuery();
            while (rs.next()) calls.add(map(rs));

        } catch (Exception e) {
            System.out.println("[CallDAO] Erreur findAll : " + e.getMessage());
        }

        return calls;
    }

    public List<DbCall> findCallHistory(String username) {
        List<DbCall> calls = new ArrayList<>();
        String sql = """
                SELECT * FROM calls
                WHERE caller = ? OR callee = ?
                   OR (group_id IS NOT NULL AND group_id IN (
                       SELECT group_id FROM group_members WHERE username = ?
                   ))
                ORDER BY started_at DESC
                """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, username);
            ps.setString(3, username);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) calls.add(map(rs));

        } catch (Exception e) {
            System.out.println("[CallDAO] Erreur findCallHistory : " + e.getMessage());
        }

        return calls;
    }

    public List<DbCall> findCallsBetween(String user1, String user2) {
        List<DbCall> calls = new ArrayList<>();
        String sql = """
                SELECT * FROM calls
                WHERE group_id IS NULL
                  AND ((caller = ? AND callee = ?) OR (caller = ? AND callee = ?))
                ORDER BY started_at DESC
                """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user1);
            ps.setString(2, user2);
            ps.setString(3, user2);
            ps.setString(4, user1);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) calls.add(map(rs));

        } catch (Exception e) {
            System.out.println("[CallDAO] Erreur findCallsBetween : " + e.getMessage());
        }

        return calls;
    }

    public Integer findLastPendingCallId(String caller, String callee) {
        String sql = """
                SELECT id FROM calls
                WHERE caller = ? AND callee = ? AND call_status = 'PENDING'
                ORDER BY started_at DESC
                LIMIT 1
                """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, caller);
            ps.setString(2, callee);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("id");

        } catch (Exception e) {
            System.out.println("[CallDAO] Erreur findLastPendingCallId : " + e.getMessage());
        }

        return null;
    }

    public boolean markAccepted(int id) {
        String sql = """
                UPDATE calls
                SET call_status = 'ACCEPTED', accepted_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;
        return executeUpdateById(sql, id);
    }

    public boolean markRejected(int id) {
        String sql = """
                UPDATE calls
                SET call_status = 'REJECTED', ended_at = CURRENT_TIMESTAMP, end_reason = 'REJECTED'
                WHERE id = ?
                """;
        return executeUpdateById(sql, id);
    }

    public boolean markMissed(int id) {
        String sql = """
                UPDATE calls
                SET call_status = 'MISSED', ended_at = CURRENT_TIMESTAMP, end_reason = 'MISSED'
                WHERE id = ?
                """;
        return executeUpdateById(sql, id);
    }

    public boolean endCall(int id, String reason) {
        String sql = """
                UPDATE calls
                SET call_status = 'ENDED',
                    ended_at = CURRENT_TIMESTAMP,
                    duration_seconds = CASE
                        WHEN accepted_at IS NULL THEN 0
                        ELSE TIMESTAMPDIFF(SECOND, accepted_at, CURRENT_TIMESTAMP)
                    END,
                    end_reason = ?
                WHERE id = ?
                """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, reason != null ? reason : "ENDED");
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;

        } catch (Exception e) {
            System.out.println("[CallDAO] Erreur endCall : " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean update(DbCall call) {
        String sql = """
                UPDATE calls
                SET call_type = ?, call_status = ?, end_reason = ?
                WHERE id = ?
                """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, call.getCallType());
            ps.setString(2, call.getCallStatus());
            ps.setString(3, call.getEndReason());
            ps.setInt(4, call.getId());
            return ps.executeUpdate() > 0;

        } catch (Exception e) {
            System.out.println("[CallDAO] Erreur update : " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean delete(int id) {
        String sql = "DELETE FROM calls WHERE id = ?";
        return executeUpdateById(sql, id);
    }

    private boolean executeUpdateById(String sql, int id) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            return ps.executeUpdate() > 0;

        } catch (Exception e) {
            System.out.println("[CallDAO] Erreur executeUpdateById : " + e.getMessage());
            return false;
        }
    }

    private DbCall map(ResultSet rs) throws Exception {
        return new DbCall(
                rs.getInt("id"),
                rs.getString("caller"),
                rs.getString("callee"),
                rs.getString("group_id"),
                rs.getString("call_type"),
                rs.getString("call_status"),
                rs.getString("started_at"),
                rs.getString("accepted_at"),
                rs.getString("ended_at"),
                rs.getInt("duration_seconds"),
                rs.getString("end_reason")
        );
    }
}
