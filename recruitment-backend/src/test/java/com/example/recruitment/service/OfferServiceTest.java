package com.example.recruitment.service;

import com.example.recruitment.dto.OfferPublicView;
import com.example.recruitment.dto.OfferView;
import com.example.recruitment.entity.Candidate;
import com.example.recruitment.entity.CandidateEvent;
import com.example.recruitment.entity.CandidateOffer;
import com.example.recruitment.entity.CandidateStageRecord;
import com.example.recruitment.entity.CandidateStatus;
import com.example.recruitment.entity.Client;
import com.example.recruitment.entity.ClientProcess;
import com.example.recruitment.entity.ClientRoleAssignment;
import com.example.recruitment.entity.HiringManager;
import com.example.recruitment.entity.OfferStatus;
import com.example.recruitment.entity.RecruitmentProcess;
import com.example.recruitment.entity.Requirement;
import com.example.recruitment.entity.Role;
import com.example.recruitment.entity.StageDecision;
import com.example.recruitment.exception.ResourceNotFoundException;
import com.example.recruitment.repository.CandidateEventRepository;
import com.example.recruitment.repository.CandidateOfferRepository;
import com.example.recruitment.repository.CandidateRepository;
import com.example.recruitment.repository.CandidateStageRecordRepository;
import com.example.recruitment.repository.ClientProcessRepository;
import com.example.recruitment.repository.ClientRoleAssignmentRepository;
import com.example.recruitment.security.AuthUser;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferServiceTest {

    CandidateOfferRepository offerRepo = mock(CandidateOfferRepository.class);
    CandidateRepository candidateRepo = mock(CandidateRepository.class);
    CandidateStageRecordRepository records = mock(CandidateStageRecordRepository.class);
    CandidateEventRepository events = mock(CandidateEventRepository.class);
    ClientProcessRepository clientProcesses = mock(ClientProcessRepository.class);
    ClientRoleAssignmentRepository roleAssignments = mock(ClientRoleAssignmentRepository.class);
    OfferLetterService letters = mock(OfferLetterService.class);
    PreonboardingService preonboarding = mock(PreonboardingService.class);
    JavaMailSender mailSender = mock(JavaMailSender.class);

    OfferService service;
    Candidate candidate;
    Client client;
    RecruitmentProcess offerAccepted;
    List<CandidateOffer> saved = new ArrayList<>();
    List<CandidateEvent> savedEvents = new ArrayList<>();

    final AuthUser hrBp = new AuthUser(5L, "hrbp@example.com", Role.HR_BP, 1L);
    final AuthUser recruiter = new AuthUser(1L, "rec@example.com", Role.RECRUITER, 1L);

    @BeforeEach
    void setUp() throws Exception {
        service = new OfferService(offerRepo, candidateRepo, records, events, clientProcesses, roleAssignments,
                letters, preonboarding, mailSender, "http://localhost:4200/", "hr@example.com", 7);

        client = new Client();
        ReflectionTestUtils.setField(client, "id", 1L);
        client.setName("Visy");

        HiringManager manager = new HiringManager();
        manager.setEmail("hm@example.com");
        Requirement requirement = new Requirement();
        requirement.setJobTitle("Full stack developer");
        requirement.setHiringManager(manager);

        candidate = new Candidate();
        ReflectionTestUtils.setField(candidate, "id", 100L);
        candidate.setClient(client);
        candidate.setRequirement(requirement);
        candidate.setFullName("Gowtham S");
        candidate.setEmail("gowtham@example.com");
        candidate.setStatus(CandidateStatus.OFFERED);

        offerAccepted = new RecruitmentProcess(OfferService.OFFER_ACCEPTED_STAGE, "Offer Accepted", null);
        ReflectionTestUtils.setField(offerAccepted, "id", 20L);

        when(candidateRepo.findById(100L)).thenReturn(Optional.of(candidate));
        when(offerRepo.save(any(CandidateOffer.class))).thenAnswer(inv -> {
            CandidateOffer o = inv.getArgument(0);
            saved.add(o);
            return o;
        });
        when(events.save(any(CandidateEvent.class))).thenAnswer(inv -> {
            CandidateEvent e = inv.getArgument(0);
            savedEvents.add(e);
            return e;
        });
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));

        ClientRoleAssignment hr = mock(ClientRoleAssignment.class);
        when(hr.getRole()).thenReturn(Role.HR_BP);
        when(hr.getEmail()).thenReturn("hrbp@example.com");
        ClientRoleAssignment rec = mock(ClientRoleAssignment.class);
        when(rec.getRole()).thenReturn(Role.RECRUITER);
        when(rec.getEmail()).thenReturn("rec@example.com");
        ClientRoleAssignment mgr = mock(ClientRoleAssignment.class);
        when(mgr.getRole()).thenReturn(Role.MANAGER);
        when(mgr.getEmail()).thenReturn("manager@example.com");
        when(roleAssignments.findByClientIdOrderByEmailAsc(1L)).thenReturn(List.of(hr, mgr, rec));
    }

    /** Sends an offer and returns the secret token that went into the email links. */
    private String issueAndGetToken() {
        when(letters.sendOffer(any(Candidate.class), anyList(), any(OfferLetterService.OfferLinks.class)))
                .thenReturn(List.of("hm@example.com", "hrbp@example.com"));
        service.issue(candidate, List.of("hm@example.com"), "hrbp@example.com");

        ArgumentCaptor<OfferLetterService.OfferLinks> links = ArgumentCaptor.forClass(OfferLetterService.OfferLinks.class);
        verify(letters).sendOffer(any(Candidate.class), anyList(), links.capture());
        String url = links.getValue().acceptUrl();
        String token = url.substring(url.indexOf("/offer/") + 7, url.indexOf('?'));
        when(offerRepo.findByTokenHash(any(String.class))).thenAnswer(inv ->
                saved.stream().filter(o -> o.getTokenHash().equals(inv.getArgument(0))).findFirst());
        return token;
    }

    @Test
    void issuingAnOfferStoresOnlyAHashAndEmailsTwoLinks() {
        String token = issueAndGetToken();

        assertEquals(1, saved.size());
        CandidateOffer offer = saved.get(0);
        assertEquals(OfferStatus.RELEASED, offer.getStatus());
        assertNotEquals(token, offer.getTokenHash(), "the secret itself must never be stored");
        assertEquals(64, offer.getTokenHash().length());
        assertEquals("hrbp@example.com", offer.getSentBy());
        assertTrue(offer.getExpiresAt().isAfter(Instant.now().plus(6, ChronoUnit.DAYS)));

        ArgumentCaptor<OfferLetterService.OfferLinks> links = ArgumentCaptor.forClass(OfferLetterService.OfferLinks.class);
        verify(letters).sendOffer(any(Candidate.class), anyList(), links.capture());
        assertTrue(links.getValue().acceptUrl().startsWith("http://localhost:4200/offer/" + token + "?action=accept"));
        assertTrue(links.getValue().declineUrl().endsWith("?action=decline"));
    }

    @Test
    void anOlderOpenOfferIsWithdrawnWhenANewOneIsIssued() {
        CandidateOffer old = new CandidateOffer();
        old.setStatus(OfferStatus.RELEASED);
        when(offerRepo.findByCandidateIdAndStatus(100L, OfferStatus.RELEASED)).thenReturn(List.of(old));
        when(letters.sendOffer(any(Candidate.class), anyList(), any(OfferLetterService.OfferLinks.class))).thenReturn(List.of());

        service.issue(candidate, List.of(), "hrbp@example.com");

        assertEquals(OfferStatus.WITHDRAWN, old.getStatus());
    }

    @Test
    void anUnknownLinkIsNotFound() {
        when(offerRepo.findByTokenHash(anyString())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.describe("nonsense"));
        assertThrows(ResourceNotFoundException.class, () -> service.describe(" "));
        assertThrows(ResourceNotFoundException.class, () -> service.describe(null));
    }

    @Test
    void openingTheLinkChangesNothing() {
        String token = issueAndGetToken();

        OfferPublicView view = service.describe(token);

        assertEquals(OfferStatus.RELEASED, view.status());
        assertTrue(view.canRespond());
        assertEquals("Gowtham S", view.candidateName());
        assertEquals("Full stack developer", view.jobTitle());
        assertEquals(CandidateStatus.OFFERED, candidate.getStatus());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void acceptingUpdatesTheStatusesAndTellsTheTeam() throws Exception {
        String token = issueAndGetToken();

        OfferPublicView view = service.respond(token, true, null);

        assertEquals(OfferStatus.ACCEPTED, view.status());
        assertFalse(view.canRespond());
        assertEquals(CandidateStatus.ACCEPTED, candidate.getStatus());
        assertNotNull(saved.get(0).getRespondedAt());
        assertTrue(savedEvents.stream().anyMatch(e -> "Offer accepted by the candidate".equals(e.getTitle())));

        ArgumentCaptor<MimeMessage> mail = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(mail.capture());
        String to = java.util.Arrays.toString(mail.getValue().getRecipients(Message.RecipientType.TO));
        assertTrue(to.contains("hm@example.com"));
        assertTrue(to.contains("hrbp@example.com"));
        assertTrue(to.contains("rec@example.com"));
        assertFalse(to.contains("manager@example.com"), "a Manager is not told");
        verify(preonboarding).createFor(candidate); // accepting starts the pre-onboarding case by itself
        assertTrue(mail.getValue().getSubject().contains("accepted"));
    }

    @Test
    void decliningClosesTheCandidateAndKeepsTheReason() {
        String token = issueAndGetToken();

        OfferPublicView view = service.respond(token, false, "  Accepted another offer  ");

        assertEquals(OfferStatus.DECLINED, view.status());
        assertEquals(CandidateStatus.DECLINED, candidate.getStatus());
        assertEquals("Accepted another offer", saved.get(0).getDeclineReason());
        verify(preonboarding, never()).createFor(any(Candidate.class)); // no pre-onboarding for a rejected offer
    }

    @Test
    void anOfferCanBeAnsweredOnlyOnce() {
        String token = issueAndGetToken();
        service.respond(token, true, null);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.respond(token, false, "changed my mind"));
        assertTrue(e.getMessage().contains("already"));
        assertEquals(CandidateStatus.ACCEPTED, candidate.getStatus());
    }

    @Test
    void anExpiredOfferCannotBeAnswered() {
        String token = issueAndGetToken();
        saved.get(0).setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));

        assertEquals(OfferStatus.EXPIRED, service.describe(token).status());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.respond(token, true, null));
        assertTrue(e.getMessage().contains("expired"));
        assertEquals(CandidateStatus.OFFERED, candidate.getStatus());
    }

    @Test
    void aWithdrawnOfferCannotBeAnswered() {
        String token = issueAndGetToken();
        saved.get(0).setStatus(OfferStatus.WITHDRAWN);

        assertThrows(IllegalArgumentException.class, () -> service.respond(token, true, null));
    }

    @Test
    void aTooLongReasonIsRefused() {
        String token = issueAndGetToken();
        assertThrows(IllegalArgumentException.class, () -> service.respond(token, false, "x".repeat(501)));
        assertEquals(CandidateStatus.OFFERED, candidate.getStatus());
    }

    @Test
    void aFailedNotificationEmailDoesNotUndoTheAnswer() {
        String token = issueAndGetToken();
        doThrow(new MailSendException("mail server down")).when(mailSender).send(any(MimeMessage.class));

        OfferPublicView view = service.respond(token, true, null);

        assertEquals(OfferStatus.ACCEPTED, view.status());
        assertEquals(CandidateStatus.ACCEPTED, candidate.getStatus());
    }

    @Test
    void acceptingApprovesTheOfferAcceptedStageWhenItIsCurrent() {
        RecruitmentProcess bgv = new RecruitmentProcess("background-verification", "Background Verification", null);
        ReflectionTestUtils.setField(bgv, "id", 21L);
        ClientProcess first = mock(ClientProcess.class);
        when(first.isEnabled()).thenReturn(true);
        when(first.getProcess()).thenReturn(offerAccepted);
        ClientProcess second = mock(ClientProcess.class);
        when(second.isEnabled()).thenReturn(true);
        when(second.getProcess()).thenReturn(bgv);
        when(clientProcesses.findByClientIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of(first, second));
        when(records.findByCandidateIdAndProcessId(100L, 20L)).thenReturn(Optional.empty());
        candidate.setCurrentProcess(offerAccepted);

        String token = issueAndGetToken();
        service.respond(token, true, null);

        ArgumentCaptor<CandidateStageRecord> record = ArgumentCaptor.forClass(CandidateStageRecord.class);
        verify(records).save(record.capture());
        assertEquals(StageDecision.APPROVED, record.getValue().getDecision());
        assertEquals(bgv, candidate.getCurrentProcess());
        assertEquals(CandidateStatus.ACCEPTED, candidate.getStatus());
    }

    @Test
    void deletingACandidateRemovesTheirOffers() {
        service.deleteForCandidate(100L);

        verify(offerRepo).deleteByCandidateId(100L);
    }

    @Test
    void onlyAnHrBpCanResendOrWithdraw() {
        assertThrows(AccessDeniedException.class, () -> service.resend(100L, recruiter));
        assertThrows(AccessDeniedException.class, () -> service.withdraw(100L, recruiter));
        assertThrows(AccessDeniedException.class, () -> service.resend(100L, null));
    }

    @Test
    void resendingGivesFreshLinksAndKeepsTheCandidateOffered() {
        issueAndGetToken();
        when(offerRepo.findFirstByCandidateIdOrderByIdDesc(100L)).thenAnswer(inv -> Optional.of(saved.get(saved.size() - 1)));

        OfferView view = service.resend(100L, hrBp);

        assertEquals(2, saved.size());
        assertEquals(OfferStatus.RELEASED, view.status());
        assertEquals(CandidateStatus.OFFERED, candidate.getStatus());
        assertTrue(savedEvents.stream().anyMatch(e -> "Offer letter re-sent".equals(e.getTitle())));
    }

    @Test
    void anAnsweredOfferCannotBeResentOrWithdrawn() {
        String token = issueAndGetToken();
        service.respond(token, true, null);
        when(offerRepo.findFirstByCandidateIdOrderByIdDesc(100L)).thenReturn(Optional.of(saved.get(0)));

        assertThrows(IllegalArgumentException.class, () -> service.resend(100L, hrBp));
        assertThrows(IllegalArgumentException.class, () -> service.withdraw(100L, hrBp));
    }

    @Test
    void withdrawingStopsTheLinksAndReopensTheCandidate() {
        String token = issueAndGetToken();
        when(offerRepo.findFirstByCandidateIdOrderByIdDesc(100L)).thenReturn(Optional.of(saved.get(0)));

        OfferView view = service.withdraw(100L, hrBp);

        assertEquals(OfferStatus.WITHDRAWN, view.status());
        assertEquals(CandidateStatus.ACTIVE, candidate.getStatus());
        assertFalse(view.canWithdraw());
        assertThrows(IllegalArgumentException.class, () -> service.respond(token, true, null));
    }
}
