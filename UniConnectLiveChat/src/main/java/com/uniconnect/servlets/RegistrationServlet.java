package com.uniconnect.servlets;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;

@WebServlet("/register")
public class RegistrationServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String username = request.getParameter("username");
        String password = request.getParameter("password");

        try (Connection conn = db.getConnection()) {
            String checkSql = "SELECT 1 FROM users WHERE username = ?";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setString(1, username);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String msg = URLEncoder.encode("Username already exists. Please choose another.", StandardCharsets.UTF_8);
                    response.sendRedirect("registration.html?msg=" + msg + "&success=0");
                    return;
                }
            }

            String insertSql = "INSERT INTO users(username, password) VALUES(?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, username);
                ps.setString(2, password); 
                ps.executeUpdate();
            }

            String msg = URLEncoder.encode("Account created successfully! Redirecting to login…", StandardCharsets.UTF_8);
            response.sendRedirect("registration.html?msg=" + msg + "&success=1");

        } catch (SQLException e) {
            String msg = URLEncoder.encode("Database error: " + e.getMessage(), StandardCharsets.UTF_8);
            response.sendRedirect("registration.html?msg=" + msg + "&success=0");
        }
    }
}
