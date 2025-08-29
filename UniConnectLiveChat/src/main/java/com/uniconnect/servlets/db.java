package com.uniconnect.servlets;

import java.nio.file.*;
import java.sql.*;

/**
 * Minimal SQLite helper for USERS only.
 * All message/history tables and functions have been removed.
 */
public class db {

    private static String URL;
    private static Path DB_FILE;

    static {
        loadDriver();
        initPaths();
        ensureSchema();
    }

    /** Called on app startup (via AppBootstrap) */
    public static void bootstrap() {
        // schema ensured in static block
    }

    private static void loadDriver() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("[DB] Could not load org.sqlite.JDBC driver");
            e.printStackTrace();
        }
    }

    private static void initPaths() {
        // GlassFish/Tomcat domain root if available, else user home
        String domainRoot = System.getProperty("com.sun.aas.instanceRoot");
        if (domainRoot == null) domainRoot = System.getProperty("user.home");

        Path dataDir = Paths.get(domainRoot, "config", "data");
        try { Files.createDirectories(dataDir); } catch (Exception ignore) {}
        DB_FILE = dataDir.resolve("uniconnect.sqlite");
        URL = "jdbc:sqlite:" + DB_FILE.toAbsolutePath();
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    private static void ensureSchema() {
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            // USERS table (simple auth + optional avatar url)
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS users (" +
                "  username TEXT PRIMARY KEY," +
                "  password TEXT NOT NULL," +
                "  avatar_url TEXT" +
                ")"
            );
            // Ensure avatar_url exists for older DBs
            try { st.executeUpdate("ALTER TABLE users ADD COLUMN avatar_url TEXT"); } catch (SQLException ignore) {}

            // Remove legacy message tables if they exist
            try { st.executeUpdate("DROP TABLE IF EXISTS messages"); } catch (SQLException ignore) {}
            try { st.executeUpdate("DROP TABLE IF EXISTS room_messages"); } catch (SQLException ignore) {}
            try { st.executeUpdate("DROP TABLE IF EXISTS direct_messages"); } catch (SQLException ignore) {}

        } catch (SQLException e) {
            System.err.println("[DB] ensureSchema failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Ensure a user row exists (used by tests/seeders/UI flows). */
    public static void ensureUserExists(String username, String passwordIfNew) {
        if (username == null || username.isBlank()) return;
        try (Connection c = getConnection()) {
            // Insert if not exists
            try (PreparedStatement sel = c.prepareStatement("SELECT 1 FROM users WHERE username = ?")) {
                sel.setString(1, username);
                try (ResultSet rs = sel.executeQuery()) {
                    if (rs.next()) return;
                }
            }
            try (PreparedStatement ins = c.prepareStatement("INSERT INTO users(username, password) VALUES(?, ?)")) {
                ins.setString(1, username);
                ins.setString(2, passwordIfNew != null ? passwordIfNew : "");
                ins.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[DB] ensureUserExists failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
