package com.uniconnect.servlets;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@WebServlet("/profile/avatar")
@MultipartConfig(fileSizeThreshold = 1024 * 1024,   // 1MB in memory
        maxFileSize = 5L * 1024 * 1024,            // 5MB file
        maxRequestSize = 6L * 1024 * 1024)         // 6MB total
public class ProfileAvatarServlet extends HttpServlet {

    private Path avatarsDir;

    @Override
    public void init() throws ServletException {
        try {
            // Put files under GlassFish domain config, where your DB already lives.
            String domainRoot = System.getProperty("com.sun.aas.instanceRoot"); // e.g. …/glassfish/domains/domain1
            if (domainRoot == null) {
                domainRoot = System.getProperty("user.home");
            }
            avatarsDir = Paths.get(domainRoot, "config", "avatars");
            Files.createDirectories(avatarsDir);
        } catch (IOException e) {
            throw new ServletException("Failed to prepare avatars directory", e);
        }
    }

    private String getLoggedInUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return (session == null) ? null : (String) session.getAttribute("user");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String user = getLoggedInUser(req);
        resp.setContentType("application/json; charset=UTF-8");
        if (user == null) {
            resp.getWriter().write("{\"avatar\":null}");
            return;
        }
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT avatar_url FROM users WHERE username=?")) {
            ps.setString(1, user);
            try (ResultSet rs = ps.executeQuery()) {
                String url = (rs.next()) ? rs.getString(1) : null;
                if (url == null) resp.getWriter().write("{\"avatar\":null}");
                else resp.getWriter().write("{\"avatar\":\"" + url.replace("\"", "\\\"") + "\"}");
            }
        } catch (SQLException e) {
            resp.getWriter().write("{\"avatar\":null}");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String user = getLoggedInUser(req);
        if (user == null) { resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED); return; }
        try (Connection c = db.getConnection()) {
            // Optionally delete the old file from disk
            String old = null;
            try (PreparedStatement ps = c.prepareStatement("SELECT avatar_url FROM users WHERE username=?")) {
                ps.setString(1, user);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) old = rs.getString(1);
                }
            }
            if (old != null && old.contains("/uploads/")) {
                String fileName = old.substring(old.lastIndexOf('/') + 1);
                try { Files.deleteIfExists(avatarsDir.resolve(fileName)); } catch (Exception ignore) {}
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE users SET avatar_url=NULL WHERE username=?")) {
                ps.setString(1, user);
                ps.executeUpdate();
            }
            resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        String user = getLoggedInUser(req);
        if (user == null) { resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED); return; }

        Part filePart = req.getPart("file");
        if (filePart == null || filePart.getSize() == 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        String ct = filePart.getContentType();
        if (ct == null || !ct.startsWith("image/")) {
            resp.setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
            return;
        }

        // filename like: username_yyyyMMddHHmmss.ext
        String ext = guessExt(ct);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String safeUser = user.replaceAll("[^a-zA-Z0-9_-]", "_");
        String fileName = safeUser + "_" + stamp + ext;

        Path target = avatarsDir.resolve(fileName);
        try (InputStream in = filePart.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }

        String publicUrl = req.getContextPath() + "/uploads/" + fileName;

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET avatar_url=? WHERE username=?")) {
            ps.setString(1, publicUrl);
            ps.setString(2, user);
            ps.executeUpdate();
            resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } catch (SQLException e) {
            // cleanup file on failure
            try { Files.deleteIfExists(target); } catch (Exception ignore) {}
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private static String guessExt(String contentType) {
        switch (contentType) {
            case "image/png": return ".png";
            case "image/gif": return ".gif";
            case "image/webp": return ".webp";
            case "image/bmp": return ".bmp";
            case "image/jpeg":
            default: return ".jpg";
        }
    }
}
