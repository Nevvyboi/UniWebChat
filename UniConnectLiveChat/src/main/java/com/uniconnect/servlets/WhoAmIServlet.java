package com.uniconnect.servlets;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;

@WebServlet("/whoami")
public class WhoAmIServlet extends HttpServlet {
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        String user = (session != null) ? (String) session.getAttribute("user") : null;
        resp.setContentType("application/json; charset=UTF-8");
        PrintWriter out = resp.getWriter();
        if (user == null) out.print("{\"user\":null}");
        else out.print("{\"user\":\"" + user.replace("\"", "\\\"") + "\"}");
        out.flush();
    }
}
