package com.uniconnect.servlets;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.servlet.http.HttpSession;
import jakarta.websocket.CloseReason;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

import java.io.StringReader;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@ServerEndpoint(value = "/ws/chat", configurator = HttpSessionConfigurator.class)
public class ChatEndpoint {

  private static final ConcurrentHashMap<Session, String> userOf = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<Session, String> roomOf = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, CopyOnWriteArraySet<Session>> members = new ConcurrentHashMap<>();

  @OnOpen
  public void onOpen(Session s, EndpointConfig cfg) {
    HttpSession http = (HttpSession) cfg.getUserProperties().get(HttpSession.class.getName());
    String user = (http != null) ? (String) http.getAttribute("user") : null; // your login should set this
    if (user == null || user.isBlank()) user = "Anonymous-" + s.getId().substring(0, 5);
    userOf.put(s, user);
    sys(s, "Connected.");
  }

  @OnMessage
  public void onMessage(Session s, String raw) {
    JsonObject msg;
    try (JsonReader r = Json.createReader(new StringReader(raw))) { msg = r.readObject(); }
    catch (Exception e) { sys(s, "Bad JSON."); return; }

    String type = msg.getString("type", "");
    switch (type) {
      case "join": {
        String room = msg.getString("room", "general");
        String previous = roomOf.put(s, room);
        if (previous != null && !previous.equals(room)) {
          members.getOrDefault(previous, new CopyOnWriteArraySet<>()).remove(s);
        }
        members.computeIfAbsent(room, k -> new CopyOnWriteArraySet<>()).add(s);
        sys(s, "Joined room " + room);
        break;
      }
    case "room": {
      String room   = roomOf.getOrDefault(s, "general");
      String sender = userOf.getOrDefault(s, "Anonymous");

      // Accept "text" or "content" and ignore blanks
      String content = msg.getString("text", msg.getString("content", ""));
      if (content != null) content = content.trim();
      if (content == null || content.isEmpty()) {
        // Don't broadcast or store empty messages
        return;
      }

      long now = java.time.Instant.now().toEpochMilli();

      // --- Save to DB using your existing db.java ---
      try (java.sql.Connection c = com.uniconnect.servlets.db.getConnection();
           java.sql.PreparedStatement ps = c.prepareStatement(
               "INSERT INTO messages (room, sender, content, created_at) VALUES (?, ?, ?, ?)"
           )) {
        ps.setString(1, room);
        ps.setString(2, sender);
        ps.setString(3, content);
        ps.setTimestamp(4, new java.sql.Timestamp(now));
        ps.executeUpdate();
      } catch (Exception ex) {
        // Don’t fail chat on DB error; you can log if you want.
        sys(s, "Failed to save message.");
      }

      // --- Broadcast stamped message to room members ---
      jakarta.json.JsonObject out = jakarta.json.Json.createObjectBuilder()
          .add("type", "chat")
          .add("room", room)
          .add("sender", sender)
          .add("content", content)
          .add("createdAt", now)
          .build();

      broadcast(room, out.toString());
      break;
    }
      default:
        sys(s, "Unknown message type.");
    }
  }

  @OnClose
  public void onClose(Session s, CloseReason reason) {
    String room = roomOf.remove(s);
    if (room != null) members.getOrDefault(room, new CopyOnWriteArraySet<>()).remove(s);
    userOf.remove(s);
  }

  @OnError
  public void onError(Session s, Throwable t) {
    // log if needed
  }

  private void sys(Session s, String text) {
    JsonObject out = Json.createObjectBuilder().add("type", "sys").add("text", text).build();
    if (s.isOpen()) s.getAsyncRemote().sendText(out.toString());
  }

  private void broadcast(String room, String payload) {
    for (Session peer : members.getOrDefault(room, new CopyOnWriteArraySet<>())) {
      if (peer.isOpen()) peer.getAsyncRemote().sendText(payload);
    }
  }
}
