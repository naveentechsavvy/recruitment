package com.example.recruitment.service;

import com.example.recruitment.dto.AcceptInvitationRequest;
import com.example.recruitment.dto.InvitationInfoResponse;
import com.example.recruitment.entity.AppUser;
import com.example.recruitment.entity.Client;
import com.example.recruitment.entity.ClientRoleAssignment;
import com.example.recruitment.entity.InvitationToken;
import com.example.recruitment.entity.Role;
import com.example.recruitment.repository.AppUserRepository;
import com.example.recruitment.repository.ClientRepository;
import com.example.recruitment.repository.InvitationTokenRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class InvitationService {
    private static final Logger log = LoggerFactory.getLogger(InvitationService.class);

    private static final String PRODUCT_NAME = "Velzenix Nova ATS";

    private final AppUserRepository users;
    private final ClientRepository clients;
    private final InvitationTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final JavaMailSender mailSender;
    private final String frontendUrl;
    private final String publicBaseUrl;
    private final String fromAddress;
    private final int expiryHours;

    public InvitationService(AppUserRepository users, ClientRepository clients, InvitationTokenRepository tokens,
                             PasswordEncoder encoder, JavaMailSender mailSender,
                             @Value("${app.frontend-url:http://localhost:4200}") String frontendUrl,
                             @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl,
                             @Value("${app.mail.from:${spring.mail.username:}}") String fromAddress,
                             @Value("${app.invitation.expiry-hours:24}") int expiryHours) {
        this.users = users;
        this.clients = clients;
        this.tokens = tokens;
        this.encoder = encoder;
        this.mailSender = mailSender;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        this.fromAddress = fromAddress;
        this.expiryHours = expiryHours;
    }

    /**
     * Called after a role is assigned on a client. Sends the right email:
     *  - The person already has a working account: NO new account and NO password reset. They get an
     *    "access granted" email for that client and keep using their existing password.
     *  - New person, or an account that cannot log in yet (invitation pending, or access was removed earlier):
     *    the account is created or reset to pending and a fresh one-time set-password link is sent.
     *  - System admin accounts are never touched.
     * If the email cannot be sent, an IllegalArgumentException is thrown and the whole assignment is rolled back.
     */
    @Transactional
    public void processAssignment(ClientRoleAssignment assignment) {
        String email = assignment.getEmail().trim().toLowerCase(Locale.ROOT);
        Client client = clients.findById(assignment.getClientId())
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));
        String roleName = roleLabel(assignment.getRole());

        AppUser user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user != null && user.getRole() == Role.ADMIN) return;

        if (user != null && user.isActive()) {
            String link = portalUrl(client);
            String intro = "You have been given access to <strong>" + esc(client.getName()) + "</strong> as <strong>"
                    + esc(roleName) + "</strong>. Sign in with your existing password. There is nothing new to set up.";
            String note = "If you were not expecting this, you can ignore this email.";
            String plain = "You have been given access to " + client.getName() + " as " + roleName + ".\n"
                    + "Sign in with your existing password: " + link + "\n\n"
                    + "If you were not expecting this, you can ignore this email.";
            send(email, "You now have access to " + client.getName(),
                    buildHtml(client, "Access granted", intro, "Go to the portal", link, esc(note)), plain);
            return;
        }

        if (user == null) user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(UUID.randomUUID().toString()));
        user.setRole(assignment.getRole());
        user.setClientId(assignment.getRole() == Role.ADMIN ? null : assignment.getClientId());
        user.setActive(false);
        users.save(user);

        String rawToken = newToken();
        InvitationToken token = new InvitationToken();
        token.setEmail(email);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(Instant.now().plus(expiryHours, ChronoUnit.HOURS));
        tokens.deleteByEmailIgnoreCase(email);
        tokens.save(token);

        String link = frontendUrl + "/admin/set-password?token=" + rawToken;
        String intro = "You have been invited to <strong>" + esc(client.getName()) + "</strong> as <strong>"
                + esc(roleName) + "</strong>. Set your password to activate your account and sign in.";
        String note = "This link expires in " + expiryHours + " hours and can only be used once.<br><br>"
                + "If the button does not work, copy this link into your browser:<br>"
                + "<a href=\"" + esc(link) + "\" style=\"color:#f4623a;word-break:break-all;\">" + esc(link) + "</a><br><br>"
                + "If you were not expecting this invitation, you can ignore this email.";
        String plain = "You have been invited to " + client.getName() + " on " + PRODUCT_NAME + " as " + roleName + ".\n"
                + "Set your password here: " + link + "\n\n"
                + "This link expires in " + expiryHours + " hours and can only be used once.\n"
                + "If you were not expecting this invitation, you can ignore this email.";
        send(email, "You're invited to " + client.getName() + " on " + PRODUCT_NAME,
                buildHtml(client, "Welcome aboard", intro, "Set your password", link, note), plain);
    }

    /**
     * Called when a role is removed and the person has no role left on any client. The account row is kept
     * (other data may point to it), but it is deactivated, its password is scrambled and any unused
     * invitation link is deleted. System admin accounts are never touched.
     */
    @Transactional
    public void revokeAccess(String rawEmail) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        users.findByEmailIgnoreCase(email).ifPresent(user -> {
            if (user.getRole() == Role.ADMIN) return;
            user.setActive(false);
            user.setPasswordHash(encoder.encode(UUID.randomUUID().toString()));
        });
        tokens.deleteByEmailIgnoreCase(email);
    }

    /** For the set-password page: who is invited and to which client. Does not use up the link. */
    @Transactional(readOnly = true)
    public InvitationInfoResponse describe(String rawToken) {
        InvitationToken token = tokens.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("Invitation link is invalid or has expired"));
        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Invitation link is invalid or has expired");
        }
        AppUser user = users.findByEmailIgnoreCase(token.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invitation account was not found"));
        Client client = user.getClientId() == null ? null : clients.findById(user.getClientId()).orElse(null);
                return new InvitationInfoResponse(token.getEmail(),
                client == null ? null : client.getName(),
                client == null ? null : absoluteLogoUrl(client),
                client == null ? null : client.getSlug());
    }

    @Transactional
    public void accept(AcceptInvitationRequest request) {
        InvitationToken token = tokens.findByTokenHash(hash(request.token()))
                .orElseThrow(() -> new IllegalArgumentException("Invitation link is invalid or has expired"));
        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Invitation link is invalid or has expired");
        }
        AppUser user = users.findByEmailIgnoreCase(token.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invitation account was not found"));
        user.setPasswordHash(encoder.encode(request.password()));
        user.setActive(true);
        token.setUsedAt(Instant.now());
    }

    // ---------------------------------------------------------------- email

    private void send(String to, String subject, String html, String plain) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            if (fromAddress != null && !fromAddress.isBlank()) helper.setFrom(fromAddress, PRODUCT_NAME);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(plain, html);
            mailSender.send(message);
            log.info("Email '{}' sent to {}", subject, to);
        } catch (MailException | MessagingException | UnsupportedEncodingException ex) {
            log.error("Email failed for {}", to, ex);
            throw new IllegalArgumentException("The email could not be sent. Check the SMTP settings and try again.");
        }
    }

    /**
     * The client's name is always shown. The logo is added only when its address can be reached from the internet:
     * Gmail loads images through Google's servers, so a localhost address never works there. Set PUBLIC_BASE_URL
     * to the public address of this backend when you deploy.
     */
    private String buildHtml(Client client, String heading, String introHtml, String buttonLabel, String link, String noteHtml) {
        String logo = absoluteLogoUrl(client);
        String logoHtml = (logo != null && !isLocalAddress(logo))
                ? "<img src=\"" + esc(logo) + "\" alt=\"" + esc(client.getName())
                  + "\" style=\"max-height:56px;max-width:200px;display:block;margin:0 auto 10px auto;\">"
                : "";
        return "<!DOCTYPE html><html><body style=\"margin:0;padding:0;background-color:#f4f1ee;font-family:Arial,Helvetica,sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#f4f1ee;padding:32px 12px;\">"
                + "<tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"560\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:560px;width:100%;background-color:#ffffff;border-radius:12px;overflow:hidden;\">"
                // header
                + "<tr><td style=\"background-color:#131a2e;padding:22px 32px;\">"
                + "<span style=\"font-size:18px;font-weight:bold;color:#ffffff;letter-spacing:0.3px;\">" + esc(PRODUCT_NAME) + "</span></td></tr>"
                // client name and logo
                + "<tr><td align=\"center\" style=\"padding:28px 32px 0 32px;\">" + logoHtml
                + "<div style=\"font-size:18px;font-weight:bold;color:#35271f;\">" + esc(client.getName()) + "</div></td></tr>"
                // heading and intro
                + "<tr><td style=\"padding:20px 32px 8px 32px;\">"
                + "<h1 style=\"margin:0 0 14px 0;font-size:22px;color:#35271f;text-align:center;\">" + esc(heading) + "</h1>"
                + "<p style=\"margin:0 0 14px 0;font-size:15px;line-height:1.6;color:#51433a;\">" + introHtml + "</p></td></tr>"
                // button
                + "<tr><td align=\"center\" style=\"padding:12px 32px 20px 32px;\">"
                + "<a href=\"" + esc(link) + "\" style=\"display:inline-block;background-color:#f4623a;color:#ffffff;text-decoration:none;"
                + "font-size:15px;font-weight:bold;padding:14px 32px;border-radius:8px;\">" + esc(buttonLabel) + "</a></td></tr>"
                // notes
                + "<tr><td style=\"padding:0 32px 24px 32px;border-bottom:1px solid #eee4dc;\">"
                + "<p style=\"margin:0;font-size:13px;line-height:1.6;color:#776b64;\">" + noteHtml + "</p></td></tr>"
                // footer
                + "<tr><td style=\"padding:18px 32px 24px 32px;\">"
                + "<p style=\"margin:0;font-size:12px;line-height:1.6;color:#9a8f88;\">Sent by " + esc(PRODUCT_NAME) + "</p></td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    /** Where the person goes after access is granted: that client's own login page. */
    private String portalUrl(Client client) {
        String slug = client.getSlug();
        return (slug == null || slug.isBlank()) ? frontendUrl : frontendUrl + "/app/" + slug + "/login";
    }

    private String absoluteLogoUrl(Client client) {
        String logo = client.getLogoUrl();
        if (logo == null || logo.isBlank()) return null;
        if (logo.startsWith("http://") || logo.startsWith("https://")) return logo;
        return publicBaseUrl + (logo.startsWith("/") ? logo : "/" + logo);
    }

    private static boolean isLocalAddress(String url) {
        return url.contains("//localhost") || url.contains("//127.0.0.1");
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String roleLabel(Role role) {
        if (role == null) return "a team member";
        if ("HR_BP".equals(role.name())) return "HR BP";
        StringBuilder label = new StringBuilder();
        for (String part : role.name().split("_")) {
            if (label.length() > 0) label.append(' ');
            label.append(part.charAt(0)).append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return label.toString();
    }

    // ---------------------------------------------------------------- token helpers

    private static String newToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to process invitation token", ex);
        }
    }
}