package com.uniconnect.servlets;

import java.nio.file.*;
import java.sql.*;

/**
 * SQLite helper: creates schema on first use and provides helpers
 * used by servlets and the ChatEndpoint.
 */
public class db {

    private static String URL;
    private static Path DB_FILE;

    static {
        loadDriver();
        initPaths();
        // try to bootstrap eagerly (safe to run more than once)
        bootstrap();
    }

    private static void loadDriver() {
        try {
            Class.forName("org.sqlite.JDBC"); // ensure driver is registered on GlassFish
        } catch (ClassNotFoundException e) {
            System.err.println("[DB] Could not load org.sqlite.JDBC driver");
            e.printStackTrace();
        }
    }

    private static void initPaths() {
        String domainRoot = System.getProperty("com.sun.aas.instanceRoot"); // e.g. .../glassfish/domains/domain1
        if (domainRoot == null) domainRoot = System.getProperty("user.home");
        Path dataDir = Paths.get(domainRoot, "config", "data");
        try { Files.createDirectories(dataDir); } catch (Exception ignore) {}
        DB_FILE = dataDir.resolve("uniconnect.db");
        URL = "jdbc:sqlite:" + DB_FILE.toString();
        System.out.println("[DB] Using SQLite file: " + DB_FILE.toAbsolutePath());
    }

    /** Public bootstrap — safe to call multiple times. */
    public static synchronized void bootstrap() {
        ensureSchema();
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    private static void ensureSchema() {
        try (Connection c = getConnection();
             Statement st = c.createStatement()) {

            // USERS
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS users (" +
                "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  username TEXT UNIQUE NOT NULL," +
                "  password TEXT NOT NULL," +
                "  avatar_url TEXT" +
                ")"
            );
            // add column if DB existed already without it
            try { st.executeUpdate("ALTER TABLE users ADD COLUMN avatar_url TEXT"); } catch (SQLException ignore) {}

            // MESSAGES
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS messages (" +
                "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  room TEXT," +                 // for group chat
                "  sender TEXT NOT NULL," +
                "  recipient TEXT," +            // for DMs
                "  content TEXT NOT NULL," +
                "  sent_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );

            try { st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_msg_room ON messages(room, sent_at DESC)"); } catch (SQLException ignore) {}
            try { st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_msg_dm   ON messages(sender, recipient, sent_at DESC)"); } catch (SQLException ignore) {}

            System.out.println("[DB] Schema ensured (users, messages).");

        } catch (SQLException e) {
            System.err.println("[DB] Schema bootstrap failed for " + DB_FILE.toAbsolutePath());
            e.printStackTrace();
        }
    }

    // ---------- helpers used by ChatEndpoint ----------

    public static void insertRoomMessage(String room, String sender, String content) {
        final String sql = "INSERT INTO messages (room, sender, recipient, content) VALUES (?, ?, NULL, ?)";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, room);
            ps.setString(2, sender);
            ps.setString(3, content);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void insertDirectMessage(String recipient, String sender, String content) {
        final String sql = "INSERT INTO messages (room, sender, recipient, content) VALUES (NULL, ?, ?, ?)";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, sender);
            ps.setString(2, recipient);
            ps.setString(3, content);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static String getAvatar(String username) {
        final String sql = "SELECT avatar_url FROM users WHERE username = ?";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // Optional utility
    public static void ensureUserExists(String username, String passwordIfNew) {
        final String sel = "SELECT 1 FROM users WHERE username=?";
        final String ins = "INSERT INTO users(username, password) VALUES(?,?)";
        try (Connection c = getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(sel)) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return; }
            }
            try (PreparedStatement ps = c.prepareStatement(ins)) {
                ps.setString(1, username);
                ps.setString(2, passwordIfNew != null ? passwordIfNew : "");
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
