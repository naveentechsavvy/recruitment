
package com.example.recruitment.controller;

import com.example.recruitment.dto.AcceptInvitationRequest;
import com.example.recruitment.dto.InvitationInfoResponse;
import com.example.recruitment.dto.LoginRequest;
import com.example.recruitment.dto.LoginResponse;
import com.example.recruitment.entity.AppUser;
import com.example.recruitment.entity.Client;
import com.example.recruitment.entity.Role;
import com.example.recruitment.repository.AppUserRepository;
import com.example.recruitment.repository.ClientRepository;
import com.example.recruitment.security.AuthUser;
import com.example.recruitment.security.JwtService;
import com.example.recruitment.security.RoleResolver;
import com.example.recruitment.service.InvitationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserRepository users;
    private final ClientRepository clients;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final RoleResolver roleResolver;
    private final InvitationService invitations;

    public AuthController(
            AppUserRepository users,
            ClientRepository clients,
            PasswordEncoder encoder,
            JwtService jwtService,
            RoleResolver roleResolver,
            InvitationService invitations) {
        this.roleResolver = roleResolver;
        this.invitations = invitations;
        this.users = users;
        this.clients = clients;
        this.encoder = encoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/accept-invitation")
    public ResponseEntity<?> acceptInvitation(
            @Valid @RequestBody AcceptInvitationRequest request) {
        invitations.accept(request);
        return ResponseEntity.ok(
                Map.of("message",
                        "Password set successfully. You can now log in."));
    }

    @GetMapping("/invitation")
    public InvitationInfoResponse invitation(
            @RequestParam("token") String token) {
        return invitations.describe(token);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request) {

        AppUser user = users
                .findByEmailIgnoreCase(request.email().trim())
                .orElse(null);

        if (user == null
                || !user.isActive()
                || !encoder.matches(
                        request.password(), user.getPasswordHash())) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "message", "Invalid email or password"));
        }

        Long selectedClientId = null;
        String portal = request.portal();
        boolean adminPortalLogin = portal == null || portal.isBlank();

        // Admin Portal login (no client slug): only system administrators.
        if (adminPortalLogin && user.getRole() != Role.ADMIN) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "message",
                            "This account does not have Admin Portal access. "
                                    + "Please sign in through your client's login page."));
        }

        // Client website login: only invited client users. A system administrator account never signs in here.
        if (!adminPortalLogin && user.getRole() == Role.ADMIN) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "message",
                            "You do not have access to this client portal"));
        }

        if (portal != null && !portal.isBlank()) {
            Client client = clients.findBySlugIgnoreCase(portal.trim())
                    .orElse(null);

            if (client == null) {
                return ResponseEntity
                        .status(HttpStatus.FORBIDDEN)
                        .body(Map.of(
                                "message",
                                "You do not have access to this client portal"));
            }

            selectedClientId = client.getId();
        }

        AuthUser me = roleResolver.resolve(user, selectedClientId)
                .orElse(null);

        if (me == null) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "message",
                            "You do not have access to this client portal"));
        }

        return ResponseEntity.ok(
                new LoginResponse(
                        jwtService.generate(user, selectedClientId,
                                adminPortalLogin ? JwtService.PORTAL_ADMIN : JwtService.PORTAL_CLIENT),
                        user.getEmail(),
                        me.role(),
                        me.clientId()));
    }

    /** The Admin Portal calls this on start-up to confirm, on the server, who the stored token really belongs to. */
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthUser me, Authentication auth) {
        return new MeResponse(me.email(), me.role(), me.clientId(),
                auth.getDetails() == null ? null : auth.getDetails().toString());
    }

    public record MeResponse(String email, Role role, Long clientId, String portal) { }
}