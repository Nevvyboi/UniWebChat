package com.uniconnect.servlets;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String username = request.getParameter("username");
        String password = request.getParameter("password");

        try (Connection conn = db.getConnection()) {
            String sql = "SELECT password FROM users WHERE username = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ResultSet rs = ps.executeQuery();

                if (!rs.next()) {
                    response.sendRedirect("login.html?msg=" + enc("User not found.") + "&success=0");
                    return;
                }
                String stored = rs.getString("password");
                if (!stored.equals(password)) {
                    response.sendRedirect("login.html?msg=" + enc("Incorrect password.") + "&success=0");
                    return;
                }
            }

            request.getSession(true).setAttribute("user", username);
            response.sendRedirect(request.getContextPath() + "/home.html");

        } catch (SQLException e) {
            response.sendRedirect("login.html?msg=" + enc("Database error: " + e.getMessage()) + "&success=0");
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
