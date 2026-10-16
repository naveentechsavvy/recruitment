package com.example.recruitment.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.recruitment.entity.Role;


/**
 * Runs after the login filter. A HIRING_MANAGER (or a recruiter assigned to one client) may only call /api/clients/{id}/... for his own client,
 * so a manager of one client can no longer read another client's requirements or managers.
 */
public class ClientScopeFilter extends OncePerRequestFilter {

    private static final Pattern CLIENT_PATH = Pattern.compile("^/api/clients/(\\d+)(/.*)?$");

    @Override
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    // A website editor may only use the /api/site endpoints (login and public pages are open to everyone anyway).
    if (auth != null && auth.getPrincipal() instanceof AuthUser editor && editor.role() == Role.CLIENT_ADMIN) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/") && !path.startsWith("/api/site/")
                && !path.startsWith("/api/public/") && !path.startsWith("/api/auth/")) {
            response.setStatus(403);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"You can only edit your own website\"}");
            return;
        }
    }

    if (auth != null && auth.getPrincipal() instanceof AuthUser user && ClientAccess.isLimitedToOneClient(user)) {
        Matcher m = CLIENT_PATH.matcher(request.getRequestURI());
        if (m.matches() && !m.group(1).equals(String.valueOf(user.clientId()))) {
            response.setStatus(403);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"You can only access your own client's data\"}");
            return;
        }
    }
    chain.doFilter(request, response);
}
}