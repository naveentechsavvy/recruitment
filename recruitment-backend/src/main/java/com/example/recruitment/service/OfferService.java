package com.example.recruitment.service;

import com.example.recruitment.dto.OfferPublicView;
import com.example.recruitment.dto.OfferView;
import com.example.recruitment.entity.Candidate;
import com.example.recruitment.entity.CandidateEvent;
import com.example.recruitment.entity.CandidateOffer;
import com.example.recruitment.entity.CandidateStageRecord;
import com.example.recruitment.entity.CandidateStatus;
import com.example.recruitment.entity.ClientProcess;
import com.example.recruitment.entity.ClientRoleAssignment;
import com.example.recruitment.entity.EventType;
import com.example.recruitment.entity.OfferStatus;
import com.example.recruitment.entity.RecruitmentProcess;
import com.example.recruitment.entity.Role;
import com.example.recruitment.entity.StageDecision;
import com.example.recruitment.exception.ResourceNotFoundException;
import com.example.recruitment.entity.Requirement;
import com.example.recruitment.repository.CandidateEventRepository;
import com.example.recruitment.repository.CandidateOfferRepository;
import com.example.recruitment.repository.CandidateRepository;
import com.example.recruitment.repository.CandidateStageRecordRepository;
import com.example.recruitment.repository.ClientProcessRepository;
import com.example.recruitment.repository.ClientRoleAssignmentRepository;
import com.example.recruitment.security.AuthUser;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.access.AccessDeniedException;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The life of an offer after the letter is created: the secure Accept / Reject links, the candidate's answer,
 * the status changes and the notifications to the hiring team.
 *
 * Safety rules:
 *  - The link holds a random secret. Only its SHA-256 hash is stored.
 *  - Opening the link never decides anything. The candidate must press Confirm on the page (a POST).
 *  - An offer can be answered once, and only before it expires or is withdrawn.
 *  - If the notification email fails, the candidate's answer is still saved. Only sending the offer itself is all-or-nothing.
 */
@Service
public class OfferService {

    /** The stage that is approved automatically when the candidate accepts, if the client uses it. */
    public static final String OFFER_ACCEPTED_STAGE = "offer-accepted";

    private static final Logger log = LoggerFactory.getLogger(OfferService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_REASON = 500;

    private final CandidateOfferRepository offers;
    private final CandidateRepository candidates;
    private final CandidateStageRecordRepository records;
    private final CandidateEventRepository events;
    private final ClientProcessRepository clientProcesses;
    private final ClientRoleAssignmentRepository roleAssignments;
    private final OfferLetterService letters;
    private final PreonboardingService preonboarding;
    private final JavaMailSender mailSender;
    private final String frontendUrl;
    private final String fromAddress;
    private final int expiryDays;

    public OfferService(CandidateOfferRepository offers,
                        CandidateRepository candidates,
                        CandidateStageRecordRepository records,
                        CandidateEventRepository events,
                        ClientProcessRepository clientProcesses,
                        ClientRoleAssignmentRepository roleAssignments,
                        OfferLetterService letters,
                        PreonboardingService preonboarding,
                        JavaMailSender mailSender,
                        @Value("${app.frontend-url:http://localhost:4200}") String frontendUrl,
                        @Value("${app.mail.from:${spring.mail.username:}}") String fromAddress,
                        @Value("${app.offer.expiry-days:7}") int expiryDays) {
        this.offers = offers;
        this.candidates = candidates;
        this.records = records;
        this.events = events;
        this.clientProcesses = clientProcesses;
        this.roleAssignments = roleAssignments;
        this.letters = letters;
        this.preonboarding = preonboarding;
        this.mailSender = mailSender;
        this.frontendUrl = frontendUrl == null ? "" : frontendUrl.replaceAll("/+$", "");
        this.fromAddress = fromAddress;
        this.expiryDays = Math.max(1, expiryDays);
    }

    // ---------------------------------------------------------------- sending

    /**
     * Creates the secure links and emails the offer letter with an Accept and a Reject button.
     * Any offer that is still open for this candidate is replaced (withdrawn). If the email cannot be sent an
     * IllegalArgumentException is thrown and the caller's transaction undoes everything, links included.
     *
     * @return the CC addresses the email was really sent to
     */
    @Transactional
    public List<String> issue(Candidate candidate, List<String> cc, String actor) {
        if (candidate.getId() != null) {
            for (CandidateOffer open : offers.findByCandidateIdAndStatus(candidate.getId(), OfferStatus.RELEASED)) {
                open.setStatus(OfferStatus.WITHDRAWN);
            }
        }

        String rawToken = newToken();
        CandidateOffer offer = new CandidateOffer();
        offer.setCandidate(candidate);
        offer.setTokenHash(hash(rawToken));
        offer.setExpiresAt(Instant.now().plus(expiryDays, ChronoUnit.DAYS));
        offer.setSentBy(actor);
        offers.save(offer);

        String page = frontendUrl + "/offer/" + rawToken;
        return letters.sendOffer(candidate, cc, new OfferLetterService.OfferLinks(
                page + "?action=accept", page + "?action=decline", offer.getExpiresAt()));
    }

    // ---------------------------------------------------------------- the candidate's side (no login)

    /** For the offer page. Does not change anything, so opening the link (or a mail scanner doing it) decides nothing. */
    @Transactional(readOnly = true)
    public OfferPublicView describe(String rawToken) {
        return publicView(find(rawToken));
    }

    /** The candidate confirmed Accept or Reject. */
    @Transactional
    public OfferPublicView respond(String rawToken, boolean accept, String reason) {
        CandidateOffer offer = find(rawToken);

        OfferStatus state = effective(offer);
        if (state != OfferStatus.RELEASED) throw new IllegalArgumentException(notOpenMessage(state));

        Candidate c = offer.getCandidate();
        PortalGuard.requireActive(c.getClient());
        if (c.getStatus() != CandidateStatus.OFFERED) {
            throw new IllegalArgumentException("This offer is no longer open. Please contact the HR team.");
        }

        String why = clean(reason);
        if (!accept && why.length() > MAX_REASON) {
            throw new IllegalArgumentException("Please keep the reason under " + MAX_REASON + " characters");
        }

        offer.setRespondedAt(Instant.now());

        if (accept) {
            offer.setStatus(OfferStatus.ACCEPTED);
            c.setStatus(CandidateStatus.ACCEPTED);
            log(c, "Offer accepted by the candidate", "The candidate accepted the offer through the secure link.", c.getEmail());
            completeOfferAcceptedStage(c);
            preonboarding.createFor(c); // the pre-onboarding case starts by itself, nobody has to create it
        } else {
            offer.setStatus(OfferStatus.DECLINED);
            offer.setDeclineReason(why.isEmpty() ? null : why);
            c.setStatus(CandidateStatus.DECLINED);
            log(c, "Offer declined by the candidate", why.isEmpty() ? "No reason was given." : why, c.getEmail());
        }

        notifyTeam(c, accept, why);
        return publicView(offer);
    }

    // ---------------------------------------------------------------- the hiring team's side

    @Transactional(readOnly = true)
    public OfferView latest(Long candidateId, AuthUser user) {
        return offers.findFirstByCandidateIdOrderByIdDesc(candidateId).map(o -> view(o, user)).orElse(null);
    }

    /** HR BP: sends the offer again with fresh links. The older links stop working. */
    @Transactional
    public OfferView resend(Long candidateId, AuthUser user) {
        requireOfferManager(user);
        Candidate c = candidate(candidateId);
        PortalGuard.requireActive(c.getClient());

        CandidateOffer last = offers.findFirstByCandidateIdOrderByIdDesc(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("No offer has been sent to this candidate yet"));
        if (last.getStatus() == OfferStatus.ACCEPTED || last.getStatus() == OfferStatus.DECLINED) {
            throw new IllegalArgumentException("The candidate has already answered this offer");
        }
        if (c.getStatus() != CandidateStatus.OFFERED && c.getStatus() != CandidateStatus.ACTIVE) {
            throw new IllegalArgumentException("This candidate is not at the offer step");
        }

        List<String> sentCc = issue(c, ccFor(c), user.email());
        c.setStatus(CandidateStatus.OFFERED);
        log(c, "Offer letter re-sent",
                "To: " + c.getEmail() + (sentCc.isEmpty() ? "" : "\nCC: " + String.join(", ", sentCc)), user.email());

        return offers.findFirstByCandidateIdOrderByIdDesc(candidateId).map(o -> view(o, user)).orElse(null);
    }

    /** HR BP: cancels an offer that is still waiting for an answer. The links stop working. */
    @Transactional
    public OfferView withdraw(Long candidateId, AuthUser user) {
        requireOfferManager(user);
        Candidate c = candidate(candidateId);
        PortalGuard.requireActive(c.getClient());

        CandidateOffer last = offers.findFirstByCandidateIdOrderByIdDesc(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("No offer has been sent to this candidate yet"));
        if (last.getStatus() != OfferStatus.RELEASED) {
            throw new IllegalArgumentException("Only an offer that is waiting for an answer can be withdrawn");
        }

        last.setStatus(OfferStatus.WITHDRAWN);
        if (c.getStatus() == CandidateStatus.OFFERED) c.setStatus(CandidateStatus.ACTIVE);
        log(c, "Offer withdrawn", "The offer was withdrawn and its links no longer work.", user.email());

        return view(last, user);
    }

    /** Removes every offer of a candidate. Called only when the candidate is deleted, so their links stop working too. */
    @Transactional
    public void deleteForCandidate(Long candidateId) {
        offers.deleteByCandidateId(candidateId);
    }

    // ---------------------------------------------------------------- helpers

    private void requireOfferManager(AuthUser user) {
        if (user == null || (user.role() != Role.HR_BP && user.role() != Role.ADMIN)) {
            throw new AccessDeniedException("Only an HR BP can resend or withdraw an offer");
        }
    }

    private Candidate candidate(Long id) {
        return candidates.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found with id " + id));
    }

    private CandidateOffer find(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 200) {
            throw new ResourceNotFoundException("This offer link is not valid");
        }
        return offers.findByTokenHash(hash(rawToken.trim()))
                .orElseThrow(() -> new ResourceNotFoundException("This offer link is not valid"));
    }

    /** A RELEASED offer whose date has passed counts as expired. */
    static OfferStatus effective(CandidateOffer o) {
        if (o.getStatus() == OfferStatus.RELEASED && o.getExpiresAt() != null && o.getExpiresAt().isBefore(Instant.now())) {
            return OfferStatus.EXPIRED;
        }
        return o.getStatus();
    }

    private static String notOpenMessage(OfferStatus state) {
        return switch (state) {
            case ACCEPTED -> "You have already accepted this offer.";
            case DECLINED -> "You have already rejected this offer.";
            case EXPIRED -> "This offer link has expired. Please contact the HR team.";
            case WITHDRAWN -> "This offer is no longer open. Please contact the HR team.";
            default -> "This offer is not open.";
        };
    }

    private OfferPublicView publicView(CandidateOffer o) {
        Candidate c = o.getCandidate();
        Requirement r = c.getRequirement();
        OfferStatus state = effective(o);
        return new OfferPublicView(
                c.getFullName(),
                r == null ? null : r.getJobTitle(),
                c.getClient() == null ? null : c.getClient().getName(),
                state,
                o.getExpiresAt(),
                o.getRespondedAt(),
                state == OfferStatus.RELEASED && c.getStatus() == CandidateStatus.OFFERED
                        && (c.getClient() == null || !c.getClient().isFrozen()));
    }

    private OfferView view(CandidateOffer o, AuthUser user) {
        Candidate c = o.getCandidate();
        OfferStatus state = effective(o);
        boolean manager = user != null && (user.role() == Role.HR_BP || user.role() == Role.ADMIN);
        boolean frozen = c.getClient() != null && c.getClient().isFrozen();
        boolean answered = o.getStatus() == OfferStatus.ACCEPTED || o.getStatus() == OfferStatus.DECLINED;
        boolean stage = c.getStatus() == CandidateStatus.OFFERED || c.getStatus() == CandidateStatus.ACTIVE;
        return new OfferView(
                o.getId(),
                state,
                o.getCreatedAt(),
                o.getExpiresAt(),
                o.getRespondedAt(),
                o.getDeclineReason(),
                o.getSentBy(),
                manager && !frozen && !answered && stage,
                manager && !frozen && o.getStatus() == OfferStatus.RELEASED);
    }

    /** When the candidate accepts and the Offer Accepted step is the current one, it is approved and the process moves on. */
    private void completeOfferAcceptedStage(Candidate c) {
        RecruitmentProcess current = c.getCurrentProcess();
        if (current == null || !OFFER_ACCEPTED_STAGE.equals(current.getCode())) return;

        List<ClientProcess> stages = clientProcesses.findByClientIdOrderByDisplayOrderAsc(c.getClient().getId()).stream()
                .filter(ClientProcess::isEnabled)
                .toList();
        int index = -1;
        for (int i = 0; i < stages.size(); i++) {
            if (OFFER_ACCEPTED_STAGE.equals(stages.get(i).getProcess().getCode())) index = i;
        }
        if (index < 0) return;

        CandidateStageRecord record = records.findByCandidateIdAndProcessId(c.getId(), current.getId())
                .orElseGet(() -> {
                    CandidateStageRecord r = new CandidateStageRecord();
                    r.setCandidate(c);
                    r.setProcess(current);
                    return r;
                });
        record.setDecision(StageDecision.APPROVED);
        record.setNotes("Accepted by the candidate through the offer link");
        record.setUpdatedBy(c.getEmail());
        record.setUpdatedAt(Instant.now());
        records.save(record);

        log(c, current.getName() + ": Approved", "The candidate accepted the offer.", "System");

        if (index + 1 < stages.size()) {
            RecruitmentProcess next = stages.get(index + 1).getProcess();
            c.setCurrentProcess(next);
            log(c, "Moved to " + next.getName(), null, "System");
        }
    }

    private void log(Candidate candidate, String title, String details, String actor) {
        CandidateEvent e = new CandidateEvent();
        e.setCandidate(candidate);
        e.setType(EventType.STAGE_DECISION);
        e.setTitle(title);
        e.setDetails(details);
        e.setActor(actor);
        events.save(e);
    }

    // ---------------------------------------------------------------- who is told

    /** The job's hiring manager and every HR BP of the client (the same people copied on the offer letter). */
    List<String> ccFor(Candidate c) {
        Map<String, String> unique = new LinkedHashMap<>();
        Requirement r = c.getRequirement();
        if (r != null && r.getHiringManager() != null) addEmail(unique, r.getHiringManager().getEmail());
        if (c.getClient() != null) {
            for (ClientRoleAssignment a : roleAssignments.findByClientIdOrderByEmailAsc(c.getClient().getId())) {
                if (a.getRole() == Role.HR_BP) addEmail(unique, a.getEmail());
            }
        }
        return List.copyOf(unique.values());
    }

    /** HR BP, Recruiter and Hiring Manager. */
    List<String> teamEmails(Candidate c) {
        Map<String, String> unique = new LinkedHashMap<>();
        Requirement r = c.getRequirement();
        if (r != null && r.getHiringManager() != null) addEmail(unique, r.getHiringManager().getEmail());
        if (c.getClient() != null) {
            for (ClientRoleAssignment a : roleAssignments.findByClientIdOrderByEmailAsc(c.getClient().getId())) {
                if (a.getRole() == Role.HR_BP || a.getRole() == Role.RECRUITER) addEmail(unique, a.getEmail());
            }
        }
        return List.copyOf(unique.values());
    }

    private static void addEmail(Map<String, String> unique, String email) {
        if (email == null || email.isBlank()) return;
        unique.putIfAbsent(email.trim().toLowerCase(Locale.ROOT), email.trim());
    }

    /** Best effort: a mail problem must never undo the candidate's answer. */
    private void notifyTeam(Candidate c, boolean accepted, String reason) {
        List<String> to = teamEmails(c);
        if (to.isEmpty()) {
            log.warn("Nobody to notify about the offer answer of candidate {}", c.getId());
            return;
        }

        Requirement r = c.getRequirement();
        String job = r == null || r.getJobTitle() == null ? "the position" : r.getJobTitle();
        String company = c.getClient() == null ? "" : c.getClient().getName();
        String verdict = accepted ? "accepted" : "rejected";
        String subject = "Offer " + verdict + ": " + c.getFullName() + " (" + job + ")";

        String detail = accepted
                ? "The candidate can now start the next steps."
                : (reason == null || reason.isBlank() ? "No reason was given." : "Reason: " + reason);
        String html = "<div style=\"font-family:Arial,Helvetica,sans-serif;font-size:15px;color:#35271f;line-height:1.5;\">"
                + "<p><strong>" + esc(c.getFullName()) + "</strong> has <strong>" + verdict + "</strong> the offer for <strong>"
                + esc(job) + "</strong>" + (company.isEmpty() ? "" : " at " + esc(company)) + ".</p>"
                + "<p>" + esc(detail) + "</p>"
                + "<p style=\"color:#776b64;font-size:13px;\">Open the candidate on your website to see the full history.</p></div>";
        String plain = c.getFullName() + " has " + verdict + " the offer for " + job
                + (company.isEmpty() ? "" : " at " + company) + ".\n\n" + detail + "\n";

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            if (fromAddress != null && !fromAddress.isBlank()) helper.setFrom(fromAddress, company.isEmpty() ? "Velzenix Nova ATS" : company);
            helper.setTo(to.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(plain, html);
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException | RuntimeException e) {
            log.error("Could not notify the hiring team about the offer answer of candidate {}", c.getId(), e);
        }
    }

    // ---------------------------------------------------------------- small utilities

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to process the offer link", ex);
        }
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
