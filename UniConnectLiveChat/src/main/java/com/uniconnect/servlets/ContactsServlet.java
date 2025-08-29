package com.uniconnect.servlets;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;

@WebServlet("/api/contacts")
public class ContactsServlet extends HttpServlet {
    private static String esc(String s){ return s==null?"":s.replace("\\","\\\\").replace("\"","\\\""); }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        String me = (session != null) ? (String) session.getAttribute("user") : null;
        if (me == null) {
            resp.setStatus(401);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"error\":\"unauthenticated\"}");
            return;
        }

        // ensure users table exists
        try (Connection c = db.getConnection();
             Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS users(
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  username TEXT UNIQUE NOT NULL,
                  password TEXT NOT NULL,
                  avatar TEXT
                )
            """);
        } catch (SQLException ignored) {}

        resp.setContentType("application/json; charset=UTF-8");
        StringBuilder json = new StringBuilder();
        json.append("{\"me\":\"").append(esc(me)).append("\",\"users\":[");

        boolean first = true;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT username, COALESCE(avatar,'') AS avatar " +
                 "FROM users WHERE username <> ? ORDER BY username")) {
            ps.setString(1, me);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (!first) json.append(',');
                    first = false;
                    json.append("{\"username\":\"").append(esc(rs.getString("username"))).append("\",")
                        .append("\"avatar\":\"").append(esc(rs.getString("avatar"))).append("\"}");
                }
            }
        } catch (SQLException e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"db\"}");
            return;
        }
        json.append("]}");
        resp.getWriter().write(json.toString());
    }
}
