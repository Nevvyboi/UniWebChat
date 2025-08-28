package com.uniconnect.servlets;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;

@WebServlet("/api/history")
public class HistoryServlet extends HttpServlet {
    private static volatile boolean ensured = false;
    private static String esc(String s){ return s==null?"":s.replace("\\","\\\\").replace("\"","\\\""); }

    private static void ensure(Connection c) throws SQLException {
        if (ensured) return;
        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS messages(
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  room TEXT,
                  sender TEXT NOT NULL,
                  receiver TEXT,
                  content TEXT NOT NULL,
                  created_at TEXT NOT NULL
                )
            """);
        }
        ensured = true;
    }

@Override
protected void doGet(HttpServletRequest req, HttpServletResponse resp)
    throws ServletException, IOException {

  String mode = req.getParameter("mode");
  String room = req.getParameter("room");
  int limit = 200; // adjust if you like

  if (!"room".equalsIgnoreCase(mode) || room == null || room.isBlank()) {
    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    resp.setContentType("application/json");
    var err = jakarta.json.Json.createObjectBuilder()
        .add("error", "Use ?mode=room&room=<name>").build();
    resp.getWriter().write(err.toString());
    return;
  }

  // Query DB using your db.java
  try (java.sql.Connection c = com.uniconnect.servlets.db.getConnection();
       java.sql.PreparedStatement ps = c.prepareStatement(
           // MySQL/MariaDB: LIMIT ? ; For Derby/H2/Postgres use: FETCH FIRST ? ROWS ONLY
           "SELECT id, room, sender, content, created_at FROM messages " +
           "WHERE room = ? ORDER BY created_at ASC LIMIT ?"
       )) {
    ps.setString(1, room);
    ps.setInt(2, limit);

    try (java.sql.ResultSet rs = ps.executeQuery()) {
      var arr = jakarta.json.Json.createArrayBuilder();
      while (rs.next()) {
        arr.add(jakarta.json.Json.createObjectBuilder()
            .add("id", rs.getLong("id"))
            .add("room", rs.getString("room"))
            .add("sender", rs.getString("sender"))
            .add("content", rs.getString("content"))
            .add("createdAt", rs.getTimestamp("created_at").toInstant().toEpochMilli())
        );
      }
      var out = jakarta.json.Json.createObjectBuilder().add("messages", arr).build();
      resp.setContentType("application/json");
      resp.getWriter().write(out.toString());
    }
  } catch (Exception e) {
    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    resp.setContentType("application/json");
    var err = jakarta.json.Json.createObjectBuilder()
        .add("error", "Failed to load history").build();
    resp.getWriter().write(err.toString());
  }
}
}
