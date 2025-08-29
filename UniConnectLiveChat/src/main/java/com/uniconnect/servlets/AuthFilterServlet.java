package com.uniconnect.servlets;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Set;

/**
 * Auth filter that:
 *  1) Lets WebSocket handshakes + the WS endpoint path pass through.
 *  2) Allows public/static resources and login/register endpoints.
 *  3) Requires a logged-in user (session attribute "user" or "username") for everything else.
 */
@WebFilter("/*") // If you map filters in web.xml, remove this annotation.
public class AuthFilterServlet implements Filter {

    // If your @ServerEndpoint is "/chat", set this to "/chat"
    private static final String WS_PATH = "/chat";

    private static final Set<String> STATIC_EXT = Set.of(
            ".css", ".js", ".png", ".jpg", ".jpeg", ".gif", ".svg",
            ".ico", ".webp", ".woff", ".woff2", ".ttf", ".eot", ".map"
    );

    // Public routes (relative to context)
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/",                 // landing
            "/login.html",
            "/registration.html",
            "/whoami",
            // login/register endpoints (cover both styles)
            "/LoginServlet", "/login",
            "/RegistrationServlet", "/register"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  req  = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        final String ctx    = req.getContextPath();          // e.g. /UniConnectLiveChat
        final String uri    = req.getRequestURI();           // e.g. /UniConnectLiveChat/login
        final String rel    = uri.substring(ctx.length());   // path relative to context
        final String method = req.getMethod();

        // 0) Let CORS preflight/utility calls pass
        if ("OPTIONS".equalsIgnoreCase(method)) {
            chain.doFilter(request, response);
            return;
        }

        // 1) Always allow WebSocket handshake
        String upgrade = req.getHeader("Upgrade");
        if (upgrade != null && "websocket".equalsIgnoreCase(upgrade)) {
            chain.doFilter(request, response);
            return;
        }

        // 2) Also allow direct access to the WS endpoint path
        if (rel.startsWith(WS_PATH)) {
            chain.doFilter(request, response);
            return;
        }

        // 3) Allow static files by extension
        int dot = rel.lastIndexOf('.');
        if (dot != -1) {
            String ext = rel.substring(dot).toLowerCase();
            if (STATIC_EXT.contains(ext)) {
                chain.doFilter(request, response);
                return;
            }
        }

        // 4) Allow public routes (pages + login/register actions)
        if (PUBLIC_PATHS.contains(rel)) {
            chain.doFilter(request, response);
            return;
        }

        // 5) Auth gate for everything else
        HttpSession session = req.getSession(false);
        String user = null;
        if (session != null) {
            Object u = session.getAttribute("user");
            if (u == null) u = session.getAttribute("username"); // be lenient
            if (u != null) user = String.valueOf(u);
        }

        if (user == null || user.isBlank()) {
            resp.sendRedirect(ctx + "/login.html");
            return;
        }

        chain.doFilter(request, response);
    }
}
