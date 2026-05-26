package org.example.server.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Base distante partagée pour toute l'équipe.
 * IP serveur MySQL : 100.104.161.131
 * Base : chat_app
 */
public class DatabaseManager {


    private static final String DB_NAME = "chat_app";

    // connexion serveur (sans base) pour CREATE DATABASE
    private static final String SERVER_URL =
            "jdbc:mysql://localhost:3306/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    // connexion base chat_app
    private static final String DB_URL =
            "jdbc:mysql://localhost:3306/" + DB_NAME +
                    "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "";

    // =========================================
    // CONNECTION
    // =========================================
    public static Connection getConnection() throws Exception {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    // =========================================
    // INIT DATABASE
    // =========================================
    public static void initDatabase() {
        try {
            createDatabaseIfNotExists();

            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {

                stmt.execute("SET FOREIGN_KEY_CHECKS = 0");

                stmt.execute(usersTable());
                stmt.execute(contactsTable());
                stmt.execute(groupsTable());
                stmt.execute(groupMembersTable());
                stmt.execute(filesTable());
                stmt.execute(messagesTable());
                stmt.execute(callsTable());
                stmt.execute(meetingsTable());
                stmt.execute(meetingParticipantsTable());

                stmt.execute("SET FOREIGN_KEY_CHECKS = 1");

                ensurePasswordColumn(stmt);
                ensureUsernameNotGloballyUnique(stmt);

                System.out.println("[Database] Base MySQL initialisée : " + DB_NAME);
            }

        } catch (Exception e) {
            System.out.println("[Database] Erreur initDatabase : " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================================
    // CREATE DATABASE
    // =========================================
    private static void createDatabaseIfNotExists() throws Exception {

        try (Connection conn =
                     DriverManager.getConnection(SERVER_URL, DB_USER, DB_PASSWORD);

             Statement stmt = conn.createStatement()) {

            stmt.execute(
                    "CREATE DATABASE IF NOT EXISTS " + DB_NAME +
                            " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
            );
        }
    }

    // =========================================
    // USERS
    // =========================================
    private static void ensurePasswordColumn(Statement stmt) {
        try {
            stmt.execute("""
                    ALTER TABLE users
                    ADD COLUMN password_hash VARCHAR(255) NULL
                    AFTER display_name
                    """);
            System.out.println("[Database] Colonne password_hash ajoutée.");
        } catch (Exception ignored) {
            // colonne déjà présente
        }
    }

    /** Permet plusieurs comptes avec le même nom affiché si le mot de passe diffère. */
    private static void ensureUsernameNotGloballyUnique(Statement stmt) {
        try {
            stmt.execute("ALTER TABLE users DROP INDEX username");
            System.out.println("[Database] Contrainte UNIQUE(username) retirée.");
        } catch (Exception ignored) {
            // déjà absent ou autre nom d'index
        }
        try {
            stmt.execute("CREATE INDEX idx_users_username ON users(username)");
        } catch (Exception ignored) {
        }
    }

    private static String usersTable() {
        return """
                CREATE TABLE IF NOT EXISTS users (
                    id INT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(100) NOT NULL,
                    display_name VARCHAR(150),
                    password_hash VARCHAR(255) NULL,
                    status VARCHAR(50) NOT NULL DEFAULT 'offline',
                    last_seen TIMESTAMP NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_users_status(status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                """;
    }

    // =========================================
    // CONTACTS
    // =========================================
    private static String contactsTable() {
        return """
            CREATE TABLE IF NOT EXISTS contacts (
                id INT PRIMARY KEY AUTO_INCREMENT,
                owner_username VARCHAR(100) NOT NULL,
                contact_username VARCHAR(100) NOT NULL,
                last_message_id INT NULL,
                last_call_id INT NULL,
                last_interaction_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                UNIQUE KEY uk_contacts_owner_contact(owner_username, contact_username),
                INDEX idx_contacts_owner(owner_username),
                INDEX idx_contacts_contact(contact_username),

                CONSTRAINT fk_contacts_owner FOREIGN KEY(owner_username)
                    REFERENCES users(username)
                    ON DELETE CASCADE ON UPDATE CASCADE,

                CONSTRAINT fk_contacts_contact FOREIGN KEY(contact_username)
                    REFERENCES users(username)
                    ON DELETE CASCADE ON UPDATE CASCADE

            ) ENGINE=InnoDB
            DEFAULT CHARSET=utf8mb4
            COLLATE=utf8mb4_unicode_ci;
            """;
    }
    // =========================================
    // GROUPS
    // =========================================
    private static String groupsTable() {
        return """
                CREATE TABLE IF NOT EXISTS chat_groups (
                    id VARCHAR(80) PRIMARY KEY,
                    name VARCHAR(150) NOT NULL,
                    admin_username VARCHAR(100) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_groups_admin(admin_username),
                    CONSTRAINT fk_groups_admin FOREIGN KEY(admin_username)
                        REFERENCES users(username) ON DELETE CASCADE ON UPDATE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                """;
    }

    // =========================================
    // GROUP MEMBERS
    // =========================================
    private static String groupMembersTable() {
        return """
                CREATE TABLE IF NOT EXISTS group_members (
                    id INT PRIMARY KEY AUTO_INCREMENT,
                    group_id VARCHAR(80) NOT NULL,
                    username VARCHAR(100) NOT NULL,
                    role VARCHAR(30) NOT NULL DEFAULT 'MEMBER',
                    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_group_members(group_id, username),
                    INDEX idx_group_members_user(username),
                    CONSTRAINT fk_group_members_group FOREIGN KEY(group_id)
                        REFERENCES chat_groups(id) ON DELETE CASCADE ON UPDATE CASCADE,
                    CONSTRAINT fk_group_members_user FOREIGN KEY(username)
                        REFERENCES users(username) ON DELETE CASCADE ON UPDATE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                """;
    }


    // =========================================
    // FILES
    // =========================================
    private static String filesTable() {
        return """
                CREATE TABLE IF NOT EXISTS files (
                    id INT PRIMARY KEY AUTO_INCREMENT,
                    sender VARCHAR(100) NOT NULL,
                    receiver VARCHAR(100) NULL,
                    group_id VARCHAR(80) NULL,
                    file_name VARCHAR(255) NOT NULL,
                    file_type VARCHAR(100),
                    file_size BIGINT NOT NULL DEFAULT 0,
                    storage_path VARCHAR(500),
                    data LONGBLOB NULL,
                    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_files_sender(sender),
                    INDEX idx_files_receiver(receiver),
                    INDEX idx_files_group(group_id),
                    CONSTRAINT fk_files_sender FOREIGN KEY(sender)
                        REFERENCES users(username) ON DELETE CASCADE ON UPDATE CASCADE,
                    CONSTRAINT fk_files_receiver FOREIGN KEY(receiver)
                        REFERENCES users(username) ON DELETE SET NULL ON UPDATE CASCADE,
                    CONSTRAINT fk_files_group FOREIGN KEY(group_id)
                        REFERENCES chat_groups(id) ON DELETE SET NULL ON UPDATE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                """;
    }

    // =========================================
    // MESSAGES
    // =========================================
    private static String messagesTable() {
        return """
                CREATE TABLE IF NOT EXISTS messages (
                    id INT PRIMARY KEY AUTO_INCREMENT,
                    sender VARCHAR(100) NOT NULL,
                    receiver VARCHAR(100) NULL,
                    group_id VARCHAR(80) NULL,
                    content TEXT,
                    message_type VARCHAR(30) NOT NULL DEFAULT 'TEXT',
                    file_id INT NULL,
                    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    delivered_at TIMESTAMP NULL,
                    seen_at TIMESTAMP NULL,
                    INDEX idx_messages_private(sender, receiver, sent_at),
                    INDEX idx_messages_group(group_id, sent_at),
                    INDEX idx_messages_file(file_id),
                    CONSTRAINT fk_messages_sender FOREIGN KEY(sender)
                        REFERENCES users(username) ON DELETE CASCADE ON UPDATE CASCADE,
                    CONSTRAINT fk_messages_receiver FOREIGN KEY(receiver)
                        REFERENCES users(username) ON DELETE SET NULL ON UPDATE CASCADE,
                    CONSTRAINT fk_messages_group FOREIGN KEY(group_id)
                        REFERENCES chat_groups(id) ON DELETE SET NULL ON UPDATE CASCADE,
                    CONSTRAINT fk_messages_file FOREIGN KEY(file_id)
                        REFERENCES files(id) ON DELETE SET NULL ON UPDATE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                """;
    }


    // =========================================
    // CALLS
    // =========================================
    private static String callsTable() {
        return """
                CREATE TABLE IF NOT EXISTS calls (
                    id INT PRIMARY KEY AUTO_INCREMENT,
                    caller VARCHAR(100) NOT NULL,
                    callee VARCHAR(100) NULL,
                    group_id VARCHAR(80) NULL,
                    call_type VARCHAR(20) NOT NULL,
                    call_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
                    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    accepted_at TIMESTAMP NULL,
                    ended_at TIMESTAMP NULL,
                    duration_seconds INT DEFAULT 0,
                    end_reason VARCHAR(100),
                    INDEX idx_calls_private(caller, callee, started_at),
                    INDEX idx_calls_group(group_id, started_at),
                    CONSTRAINT fk_calls_caller FOREIGN KEY(caller)
                        REFERENCES users(username) ON DELETE CASCADE ON UPDATE CASCADE,
                    CONSTRAINT fk_calls_callee FOREIGN KEY(callee)
                        REFERENCES users(username) ON DELETE SET NULL ON UPDATE CASCADE,
                    CONSTRAINT fk_calls_group FOREIGN KEY(group_id)
                        REFERENCES chat_groups(id) ON DELETE SET NULL ON UPDATE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                """;
    }



    private static String meetingsTable() {
        return """
                CREATE TABLE IF NOT EXISTS meetings (
                    id VARCHAR(80) PRIMARY KEY,
                    group_id VARCHAR(80) NOT NULL,
                    started_by VARCHAR(100) NOT NULL,
                    meeting_type VARCHAR(20) NOT NULL DEFAULT 'VIDEO',
                    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
                    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    ended_at TIMESTAMP NULL,
                    CONSTRAINT fk_meetings_group FOREIGN KEY(group_id)
                        REFERENCES chat_groups(id) ON DELETE CASCADE ON UPDATE CASCADE,
                    CONSTRAINT fk_meetings_started_by FOREIGN KEY(started_by)
                        REFERENCES users(username) ON DELETE CASCADE ON UPDATE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                """;
    }

    private static String meetingParticipantsTable() {
        return """
                CREATE TABLE IF NOT EXISTS meeting_participants (
                    id INT PRIMARY KEY AUTO_INCREMENT,
                    meeting_id VARCHAR(80) NOT NULL,
                    username VARCHAR(100) NOT NULL,
                    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    left_at TIMESTAMP NULL,
                    UNIQUE KEY uk_meeting_participants(meeting_id, username),
                    CONSTRAINT fk_meeting_participants_meeting FOREIGN KEY(meeting_id)
                        REFERENCES meetings(id) ON DELETE CASCADE ON UPDATE CASCADE,
                    CONSTRAINT fk_meeting_participants_user FOREIGN KEY(username)
                        REFERENCES users(username) ON DELETE CASCADE ON UPDATE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                """;
    }
}

