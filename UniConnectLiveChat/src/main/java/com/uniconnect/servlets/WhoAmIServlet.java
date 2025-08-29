package com.uniconnect.servlets;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@WebServlet("/whoami")
public class WhoAmIServlet extends HttpServlet {
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // Get session ID from header or parameter
        String sessionId = request.getHeader("X-Session-Id");
        if (sessionId == null) {
            sessionId = request.getParameter("sessionId");
        }
        
        // Get user from session
        HttpSession session = request.getSession(false);
        if (session != null) {
            String username = (String) session.getAttribute("user");
            if (username != null) {
                response.setContentType("application/json");
                response.getWriter().print("{\"username\": \"" + username + "\"}");
                return;
            }
        }
        
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
