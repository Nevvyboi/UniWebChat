package com.uniconnect.servlets;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import java.util.*;
import java.util.concurrent.*;

// ws://localhost:8080/UniConnectLiveChat/chat?room=ROOM&sender=NAME
@ServerEndpoint("/chat")
public class ChatEndpoint {

  private static final Map<String, Set<Session>> ROOMS = new ConcurrentHashMap<>();

  @OnOpen
  public void onOpen(Session session) {
    Map<String, List<String>> qp = session.getRequestParameterMap();
    String room = qp.getOrDefault("room", List.of("General")).get(0);
    String sender = qp.getOrDefault("sender", List.of("Someone")).get(0);

    session.getUserProperties().put("room", room);
    session.getUserProperties().put("sender", sender);
    ROOMS.computeIfAbsent(room, r -> ConcurrentHashMap.newKeySet()).add(session);

    // (optional) notify join
    // session.getAsyncRemote().sendText("{\"type\":\"system\",\"text\":\"" + sender + " joined\"}");
  }

  @OnMessage
  public void onMessage(String message, Session session) {
    String room = (String) session.getUserProperties().get("room");
    if (room == null) room = "General";
    Set<Session> peers = ROOMS.getOrDefault(room, Set.of());
    // Broadcast the exact JSON we received
    for (Session s : peers) if (s.isOpen()) s.getAsyncRemote().sendText(message);
  }

  @OnClose
  public void onClose(Session session, CloseReason reason) {
    String room = (String) session.getUserProperties().get("room");
    if (room != null) {
      Set<Session> peers = ROOMS.get(room);
      if (peers != null) peers.remove(session);
      if (peers != null && peers.isEmpty()) ROOMS.remove(room);
    }
  }

  @OnError
  public void onError(Session session, Throwable t) {
    t.printStackTrace(); // check server logs during testing
  }
}
