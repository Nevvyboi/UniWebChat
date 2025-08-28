package com.uniconnect.servlets;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;

@WebServlet("/chatServlet")
public class ChatServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // This could be used to fetch stored chat history (not implemented here)
        response.setContentType("text/plain");
        response.getWriter().println("Chat servlet is running...");
    }
}
