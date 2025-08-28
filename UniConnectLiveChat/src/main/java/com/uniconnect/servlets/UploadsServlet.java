package com.uniconnect.servlets;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.file.*;

@WebServlet("/uploads/*")
public class UploadsServlet extends HttpServlet {
    private Path avatarsDir;

    @Override
    public void init() throws ServletException {
        try {
            String domainRoot = System.getProperty("com.sun.aas.instanceRoot");
            if (domainRoot == null) domainRoot = System.getProperty("user.home");
            avatarsDir = Paths.get(domainRoot, "config", "avatars");
            Files.createDirectories(avatarsDir);
        } catch (IOException e) {
            throw new ServletException("Failed to init uploads dir", e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo(); // /filename
        if (pathInfo == null || pathInfo.length() <= 1) { resp.setStatus(HttpServletResponse.SC_NOT_FOUND); return; }

        String fileName = pathInfo.substring(1).replaceAll("[/\\\\]+", ""); // sanitize
        Path file = avatarsDir.resolve(fileName);
        if (!Files.exists(file)) { resp.setStatus(HttpServletResponse.SC_NOT_FOUND); return; }

        String ct = Files.probeContentType(file);
        if (ct == null) ct = "application/octet-stream";
        resp.setContentType(ct);
        resp.setHeader("Cache-Control","public, max-age=604800");

        try (OutputStream out = resp.getOutputStream()) {
            Files.copy(file, out);
        }
    }
}
