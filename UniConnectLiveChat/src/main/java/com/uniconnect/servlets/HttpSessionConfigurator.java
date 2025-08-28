package com.uniconnect.servlets;

import jakarta.servlet.http.HttpSession;
import jakarta.websocket.server.ServerEndpointConfig;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.HandshakeResponse;

public class HttpSessionConfigurator extends ServerEndpointConfig.Configurator {
  @Override
  public void modifyHandshake(ServerEndpointConfig sec,
                              HandshakeRequest request,
                              HandshakeResponse response) {
    HttpSession http = (HttpSession) request.getHttpSession();
    if (http != null) {
      sec.getUserProperties().put(HttpSession.class.getName(), http);
    }
  }
}
