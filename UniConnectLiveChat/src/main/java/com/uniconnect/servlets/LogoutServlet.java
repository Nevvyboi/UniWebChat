package com.uniconnect.servlets;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@WebServlet("/logout")
public class LogoutServlet extends HttpServlet {
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    HttpSession s = req.getSession(false);
    if (s != null) s.invalidate();
    String msg = URLEncoder.encode("You’ve been logged out.", StandardCharsets.UTF_8);
    resp.sendRedirect(req.getContextPath() + "/login.html?msg=" + msg + "&success=1");
  }
}
