
package com.example.recruitment.security;

import com.example.recruitment.entity.Role;
import com.example.recruitment.repository.AppUserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AppUserRepository users;
    private final RoleResolver roleResolver;

    public JwtAuthFilter(
            JwtService jwtService,
            AppUserRepository users,
            RoleResolver roleResolver) {
        this.jwtService = jwtService;
        this.users = users;
        this.roleResolver = roleResolver;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parse(header.substring(7));

                // Which login issued this token? Tokens without a known portal claim are not trusted.
                String portal = claims.get("portal", String.class);
                boolean adminToken = JwtService.PORTAL_ADMIN.equals(portal);
                boolean clientToken = JwtService.PORTAL_CLIENT.equals(portal);
                if (!adminToken && !clientToken) {
                    throw new JwtException("Unknown token portal"); // handled below: continue as anonymous
                }

                // A client-website token may never call system-administration APIs (users, role management, process templates).
                // Everything else (requirements, candidates, ...) stays open to it, and is limited by the role rules in
                // SecurityConfig and the client scoping in ClientScopeFilter.
                if (clientToken && isSystemAdminApi(request.getRequestURI())) {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\","
                            + "\"message\":\"A client website login cannot be used for the Admin Portal\"}");
                    return;
                }

                // The client ID comes from the signed JWT.
                Number clientClaim = claims.get("clientId", Number.class); // an Integer or a Long, depending on its size
                Long selectedClientId = clientClaim == null ? null : clientClaim.longValue();

                users.findByEmailIgnoreCase(claims.getSubject())
                        .filter(AppUser -> AppUser.isActive())
                        .flatMap(user ->
                                roleResolver.resolve(user, selectedClientId))
                        .filter(principal -> !adminToken || principal.role() == Role.ADMIN)
                        .ifPresent(principal -> {
                            var auth =
                                    new UsernamePasswordAuthenticationToken(
                                            principal,
                                            null,
                                            List.of(
                                                    new SimpleGrantedAuthority(
                                                            "ROLE_" + principal.role().name())
                                            ));

                            auth.setDetails(portal); // "ADMIN" or "CLIENT", read by /api/auth/me
                            SecurityContextHolder.getContext()
                                    .setAuthentication(auth);
                        });

            } catch (JwtException | IllegalArgumentException ignored) {
                // Invalid or expired token: continue as anonymous.
            }
        }

        chain.doFilter(request, response);
    }

    /** APIs that belong to the System Admin only. */
    private static boolean isSystemAdminApi(String path) {
        return path.equals("/api/users") || path.startsWith("/api/users/")
                || path.matches("/api/clients/[^/]+/roles(/.*)?")
                || path.equals("/api/recruitment-processes") || path.startsWith("/api/recruitment-processes/");
    }
}
