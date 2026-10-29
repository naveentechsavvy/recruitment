package com.example.recruitment.service.impl;

import com.example.recruitment.dto.CandidateResponse;
import com.example.recruitment.dto.RemarkRequest;
import com.example.recruitment.dto.ScreeningActionRequest;
import com.example.recruitment.dto.ScreeningUpdateRequest;
import com.example.recruitment.dto.StageUpdateRequest;
import com.example.recruitment.entity.Candidate;
import com.example.recruitment.entity.CandidateEvent;
import com.example.recruitment.entity.CandidateStageRecord;
import com.example.recruitment.entity.CandidateStatus;
import com.example.recruitment.entity.Client;
import com.example.recruitment.entity.ClientProcess;
import com.example.recruitment.entity.ClientStatus;
import com.example.recruitment.entity.EventType;
import com.example.recruitment.entity.RecruitmentProcess;
import com.example.recruitment.entity.Requirement;
import com.example.recruitment.entity.Role;
import com.example.recruitment.entity.ScreeningAction;
import com.example.recruitment.entity.ScreeningStatus;
import com.example.recruitment.entity.StageDecision;
import com.example.recruitment.exception.PortalFrozenException;
import com.example.recruitment.exception.ResourceNotFoundException;
import com.example.recruitment.repository.CandidateEventRepository;
import com.example.recruitment.repository.CandidateRepository;
import com.example.recruitment.repository.CandidateStageRecordRepository;
import com.example.recruitment.repository.ClientProcessRepository;
import com.example.recruitment.repository.ClientRepository;
import com.example.recruitment.repository.RequirementRepository;
import com.example.recruitment.security.AuthUser;
import com.example.recruitment.service.ResumeStorageService;
import com.example.recruitment.service.StagePermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Plain unit tests: no database, no Spring context. Repositories are mocked. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CandidateServiceImplTest {

    @Mock CandidateRepository candidates;
    @Mock CandidateStageRecordRepository records;
    @Mock CandidateEventRepository events;
    @Mock RequirementRepository requirements;
    @Mock ClientRepository clients;
    @Mock ClientProcessRepository clientProcesses;
    @Mock ResumeStorageService resumeStorage;

    final StagePermissionService permissions = new StagePermissionService();
    final List<CandidateEvent> savedEvents = new ArrayList<>();
    final List<CandidateStageRecord> savedRecords = new ArrayList<>();

    final AuthUser recruiter = new AuthUser(1L, "rec@example.com", Role.RECRUITER, null);
    final AuthUser hiringManager = new AuthUser(2L, "hm@example.com", Role.HIRING_MANAGER, 1L);

    CandidateServiceImpl service;
    Candidate candidate;
    RecruitmentProcess screening;
    RecruitmentProcess managerReview;

    @BeforeEach
    void setUp() {
        service = new CandidateServiceImpl(candidates, records, events, requirements, clients,
                clientProcesses, resumeStorage, permissions);

        Client client = new Client();
        ReflectionTestUtils.setField(client, "id", 1L);

        Requirement requirement = new Requirement();
        ReflectionTestUtils.setField(requirement, "id", 10L);
        requirement.setClient(client);
        requirement.setJobId("J-1");
        requirement.setJobTitle("Flutter Developer");

        screening = process(2L, StagePermissionService.SCREENING_STAGE, "Resume Screening");
        managerReview = process(3L, StagePermissionService.HIRING_MANAGER_STAGE, "Hiring Manager Review");

        when(clientProcesses.findByClientIdOrderByDisplayOrderAsc(1L))
                .thenReturn(List.of(link(client, screening, 1), link(client, managerReview, 2)));

        candidate = new Candidate();
        ReflectionTestUtils.setField(candidate, "id", 100L);
        candidate.setClient(client);
        candidate.setRequirement(requirement);
        candidate.setFullName("Asha Rao");
        candidate.setEmail("asha@example.com");
        candidate.setResumeFile("abc.pdf");
        candidate.setResumeOriginalName("asha.pdf");
        candidate.setCurrentProcess(screening);

        when(candidates.findById(100L)).thenReturn(Optional.of(candidate));
        when(candidates.findById(999L)).thenReturn(Optional.empty());
        when(candidates.save(any(Candidate.class))).thenAnswer(inv -> inv.getArgument(0));
        when(records.findByCandidateId(anyLong())).thenReturn(List.of());
        when(records.findByCandidateIdAndProcessId(anyLong(), anyLong())).thenReturn(Optional.empty());
        when(records.save(any(CandidateStageRecord.class))).thenAnswer(inv -> {
            savedRecords.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(events.findByCandidateIdOrderByCreatedAtAscIdAsc(anyLong())).thenReturn(List.of());
        when(events.save(any(CandidateEvent.class))).thenAnswer(inv -> {
            savedEvents.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
    }

    // ---------------------------------------------------------------- Reject

    @Test
    void rejectWithoutRemarksIsBlocked() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.screeningAction(100L,
                        new ScreeningActionRequest(ScreeningAction.REJECT, null), recruiter));
        assertTrue(e.getMessage().toLowerCase().contains("reason"));
        assertUntouched();
    }

    @Test
    void rejectWithWhitespaceOnlyRemarksIsBlocked() {
        assertThrows(IllegalArgumentException.class,
                () -> service.screeningAction(100L,
                        new ScreeningActionRequest(ScreeningAction.REJECT, "   \t "), recruiter));
        assertUntouched();
    }

    @Test
    void rejectWithRemarksPersistsDecisionAndHistory() {
        CandidateResponse r = service.screeningAction(100L,
                new ScreeningActionRequest(ScreeningAction.REJECT, "  Skills do not match  "), recruiter);

        assertEquals(ScreeningStatus.REJECTED, r.screeningStatus());
        assertEquals(CandidateStatus.REJECTED, candidate.getStatus());
        assertEquals(1, eventsOfType(EventType.SCREENING_REJECTED).size());

        CandidateEvent event = eventsOfType(EventType.SCREENING_REJECTED).get(0);
        assertEquals("Skills do not match", event.getDetails());
        assertEquals("rec@example.com", event.getActor());
        assertEquals(1, savedRecords.size());
        assertEquals(StageDecision.REJECTED, savedRecords.get(0).getDecision());
    }

    @Test
    void rejectingTwiceIsBlockedAndWritesNoSecondHistoryEntry() {
        service.screeningAction(100L,
                new ScreeningActionRequest(ScreeningAction.REJECT, "No fit"), recruiter);

        assertThrows(IllegalArgumentException.class,
                () -> service.screeningAction(100L,
                        new ScreeningActionRequest(ScreeningAction.REJECT, "No fit"), recruiter));
        assertEquals(1, eventsOfType(EventType.SCREENING_REJECTED).size());
    }

    @Test
    void rejectedCandidateCannotProceedOrBeMovedForward() {
        service.screeningAction(100L,
                new ScreeningActionRequest(ScreeningAction.REJECT, "No fit"), recruiter);
        fillScreeningDetails();

        assertThrows(IllegalArgumentException.class,
                () -> service.screeningAction(100L,
                        new ScreeningActionRequest(ScreeningAction.PROCEED, null), recruiter));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateStage(100L, StagePermissionService.SCREENING_STAGE,
                        new StageUpdateRequest(StageDecision.APPROVED, null, null), recruiter));

        assertEquals(CandidateStatus.REJECTED, candidate.getStatus());
        assertEquals(screening, candidate.getCurrentProcess());
    }

    // ---------------------------------------------------------------- On hold

    @Test
    void onHoldWithoutReasonIsBlocked() {
        assertThrows(IllegalArgumentException.class,
                () -> service.screeningAction(100L,
                        new ScreeningActionRequest(ScreeningAction.ON_HOLD, " "), recruiter));
        assertUntouched();
    }

    @Test
    void onHoldPersistsStatusAndHistory() {
        CandidateResponse r = service.screeningAction(100L,
                new ScreeningActionRequest(ScreeningAction.ON_HOLD, "Waiting for budget"), recruiter);

        assertEquals(ScreeningStatus.ON_HOLD, r.screeningStatus());
        assertEquals(CandidateStatus.ON_HOLD, candidate.getStatus());
        assertEquals(1, eventsOfType(EventType.SCREENING_ON_HOLD).size());
        assertEquals("Waiting for budget", eventsOfType(EventType.SCREENING_ON_HOLD).get(0).getDetails());
    }

    @Test
    void onHoldTwiceIsBlockedWithoutDuplicateHistory() {
        service.screeningAction(100L,
                new ScreeningActionRequest(ScreeningAction.ON_HOLD, "Waiting"), recruiter);

        assertThrows(IllegalArgumentException.class,
                () -> service.screeningAction(100L,
                        new ScreeningActionRequest(ScreeningAction.ON_HOLD, "Waiting"), recruiter));
        assertEquals(1, eventsOfType(EventType.SCREENING_ON_HOLD).size());
    }

    @Test
    void candidateOnHoldCannotAdvanceThroughStageControls() {
        service.screeningAction(100L,
                new ScreeningActionRequest(ScreeningAction.ON_HOLD, "Waiting"), recruiter);
        fillScreeningDetails();

        assertThrows(IllegalArgumentException.class,
                () -> service.updateStage(100L, StagePermissionService.SCREENING_STAGE,
                        new StageUpdateRequest(StageDecision.APPROVED, null, null), recruiter));

        assertEquals(screening, candidate.getCurrentProcess());
    }

    // ---------------------------------------------------------------- Proceed

    @Test
    void proceedIsBlockedWhenScreeningDetailsAreMissing() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.screeningAction(100L,
                        new ScreeningActionRequest(ScreeningAction.PROCEED, null), recruiter));

        assertTrue(e.getMessage().contains("Current CTC"));
        assertEquals(ScreeningStatus.PENDING, candidate.getScreeningStatus());
        assertTrue(savedEvents.isEmpty());
    }

    @Test
    void proceedIsBlockedWhenResumeIsMissing() {
        fillScreeningDetails();
        candidate.setResumeFile(null);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.screeningAction(100L,
                        new ScreeningActionRequest(ScreeningAction.PROCEED, null), recruiter));

        assertTrue(e.getMessage().toLowerCase().contains("resume"));
        assertEquals(ScreeningStatus.PENDING, candidate.getScreeningStatus());
    }

    @Test
    void proceedSavesDecisionMovesToManagerReviewAndLogsHistory() {
        fillScreeningDetails();

        CandidateResponse r = service.screeningAction(100L,
                new ScreeningActionRequest(ScreeningAction.PROCEED, "Good profile"), recruiter);

        assertEquals(ScreeningStatus.PROCEEDED, r.screeningStatus());
        assertEquals(StagePermissionService.HIRING_MANAGER_STAGE, r.currentStageCode());
        assertEquals(CandidateStatus.ACTIVE, candidate.getStatus());
        assertEquals(1, eventsOfType(EventType.SCREENING_PROCEEDED).size());
        assertEquals(1, eventsOfType(EventType.STAGE_MOVED).size());
        assertEquals("rec@example.com", eventsOfType(EventType.SCREENING_PROCEEDED).get(0).getActor());

        assertEquals(1, savedRecords.size());
        assertEquals(StagePermissionService.SCREENING_STAGE, savedRecords.get(0).getProcess().getCode());
        assertEquals(StageDecision.APPROVED, savedRecords.get(0).getDecision());
    }

    @Test
    void proceedingTwiceIsBlockedWithoutDuplicateHistory() {
        fillScreeningDetails();
        service.screeningAction(100L,
                new ScreeningActionRequest(ScreeningAction.PROCEED, null), recruiter);
        int eventsAfterFirst = savedEvents.size();

        assertThrows(IllegalArgumentException.class,
                () -> service.screeningAction(100L,
                        new ScreeningActionRequest(ScreeningAction.PROCEED, null), recruiter));
        assertEquals(eventsAfterFirst, savedEvents.size());
    }

    @Test
    void candidateOnHoldCanLaterProceed() {
        fillScreeningDetails();
        service.screeningAction(100L,
                new ScreeningActionRequest(ScreeningAction.ON_HOLD, "Waiting"), recruiter);

        service.screeningAction(100L,
                new ScreeningActionRequest(ScreeningAction.PROCEED, null), recruiter);

        assertEquals(CandidateStatus.ACTIVE, candidate.getStatus());
        assertEquals(ScreeningStatus.PROCEEDED, candidate.getScreeningStatus());
        assertEquals(managerReview, candidate.getCurrentProcess());
    }

    // ---------------------------------------------------------------- Screening details

    @Test
    void screeningDetailsAreSavedTrimmedAndLoggedOnlyWhenChanged() {
        ScreeningUpdateRequest request = new ScreeningUpdateRequest(
                new BigDecimal("5.5"), new BigDecimal("3"),
                new BigDecimal("800000"), new BigDecimal("1200000"),
                30, "  Flutter, Dart  ", "  Strong communicator ");

        service.updateScreening(100L, request, recruiter);

        assertEquals("Flutter, Dart", candidate.getSkills());
        assertEquals(30, candidate.getNoticePeriodDays());
        assertEquals(1, eventsOfType(EventType.SCREENING_UPDATED).size());
        assertEquals(1, eventsOfType(EventType.REMARK).size());
        assertEquals("Strong communicator", eventsOfType(EventType.REMARK).get(0).getDetails());

        service.updateScreening(100L, new ScreeningUpdateRequest(
                new BigDecimal("5.5"), new BigDecimal("3"),
                new BigDecimal("800000"), new BigDecimal("1200000"),
                30, "Flutter, Dart", null), recruiter);

        assertEquals(1, eventsOfType(EventType.SCREENING_UPDATED).size());
    }

    @Test
    void screeningDetailsAreLockedForRejectedCandidate() {
        service.screeningAction(100L,
                new ScreeningActionRequest(ScreeningAction.REJECT, "No fit"), recruiter);

        assertThrows(IllegalArgumentException.class, () -> service.updateScreening(100L,
                new ScreeningUpdateRequest(null, null, null, null, null, "Java", null), recruiter));
    }

    // ---------------------------------------------------------------- Permissions and missing data

    @Test
    void hiringManagerCannotRunScreeningActions() {
        assertThrows(AccessDeniedException.class,
                () -> service.screeningAction(100L,
                        new ScreeningActionRequest(ScreeningAction.REJECT, "No"), hiringManager));

        assertThrows(AccessDeniedException.class, () -> service.updateScreening(100L,
                new ScreeningUpdateRequest(null, null, null, null, null, null, null), hiringManager));

        assertEquals(CandidateStatus.ACTIVE, candidate.getStatus());
    }

    @Test
    void recruiterLimitedToAnotherClientCannotTouchTheCandidate() {
        AuthUser otherClientRecruiter = new AuthUser(5L, "other@example.com", Role.RECRUITER, 77L);

        assertThrows(AccessDeniedException.class, () -> service.screeningAction(100L,
                new ScreeningActionRequest(ScreeningAction.ON_HOLD, "Waiting"), otherClientRecruiter));
    }

    @Test
    void hiringManagerCannotEditStagesBeforeScreeningIsDone() {
        assertThrows(AccessDeniedException.class, () -> service.updateStage(100L,
                StagePermissionService.HIRING_MANAGER_STAGE,
                new StageUpdateRequest(StageDecision.APPROVED, null, null), hiringManager));
    }

    @Test
    void missingCandidateGivesNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> service.screeningAction(999L,
                        new ScreeningActionRequest(ScreeningAction.REJECT, "x"), recruiter));

        assertThrows(ResourceNotFoundException.class, () -> service.updateScreening(999L,
                new ScreeningUpdateRequest(null, null, null, null, null, null, null), recruiter));

        assertThrows(ResourceNotFoundException.class, () -> service.getResume(999L, recruiter));
    }

    @Test
    void candidateWithoutResumeGivesNotFoundOnDownload() {
        candidate.setResumeFile(null);
        assertThrows(ResourceNotFoundException.class, () -> service.getResume(100L, recruiter));
    }

    // ---------------------------------------------------------------- frozen portal

    private MockMultipartFile somePdf() {
        return new MockMultipartFile(
                "resume", "cv.pdf", "application/pdf", "%PDF-1.4 test".getBytes());
    }

    @Test
    void aFrozenPortalRefusesEveryCandidateChangeAndWritesNothing() {
        fillScreeningDetails();
        candidate.getClient().setFrozen(true);

        assertThrows(PortalFrozenException.class,
                () -> service.screeningAction(100L,
                        new ScreeningActionRequest(ScreeningAction.PROCEED, null), recruiter));

        assertThrows(PortalFrozenException.class,
                () -> service.screeningAction(100L,
                        new ScreeningActionRequest(ScreeningAction.REJECT, "No fit"), recruiter));

        assertThrows(PortalFrozenException.class,
                () -> service.screeningAction(100L,
                        new ScreeningActionRequest(ScreeningAction.ON_HOLD, "Wait"), recruiter));

        assertThrows(PortalFrozenException.class, () -> service.updateScreening(100L,
                new ScreeningUpdateRequest(null, null, null, null, null, "Java", null), recruiter));

        assertThrows(PortalFrozenException.class,
                () -> service.addRemark(100L, new RemarkRequest("hello"), recruiter));

        assertThrows(PortalFrozenException.class,
                () -> service.replaceResume(100L, somePdf(), recruiter));

        assertThrows(PortalFrozenException.class,
                () -> service.updateStage(100L, StagePermissionService.SCREENING_STAGE,
                        new StageUpdateRequest(StageDecision.APPROVED, null, null), recruiter));

        assertEquals(ScreeningStatus.PENDING, candidate.getScreeningStatus());
        assertEquals(CandidateStatus.ACTIVE, candidate.getStatus());
        assertEquals("abc.pdf", candidate.getResumeFile());
        assertTrue(savedEvents.isEmpty());
        assertTrue(savedRecords.isEmpty());
    }

    @Test
    void aFrozenPortalTakesNoPublicApplications() {
        candidate.getClient().setFrozen(true);
        candidate.getClient().setSlug("acme");
        candidate.getClient().setStatus(ClientStatus.ACTIVE);

        when(clients.findBySlug("acme")).thenReturn(Optional.of(candidate.getClient()));

        PortalFrozenException e = assertThrows(PortalFrozenException.class,
                () -> service.apply("acme", "J-1", "New Person", "new@example.com", null, somePdf()));

        assertTrue(e.getMessage().contains("frozen"));
        verify(candidates, never()).save(any(Candidate.class));
        assertTrue(savedEvents.isEmpty());
    }

    @Test
    void aFrozenPortalLetsNoRecruiterAddACandidate() {
        candidate.getClient().setFrozen(true);
        when(requirements.findById(10L)).thenReturn(Optional.of(candidate.getRequirement()));

        assertThrows(PortalFrozenException.class,
                () -> service.addCandidate(10L, "New Person", "new@example.com", null, somePdf(), recruiter));

        verify(candidates, never()).save(any(Candidate.class));
    }

    @Test
    void aFrozenPortalStillShowsTheCandidateButOffersNoActions() {
        candidate.getClient().setFrozen(true);

        CandidateResponse r = service.getCandidate(100L, recruiter);

        assertEquals("Asha Rao", r.fullName());
        assertFalse(r.canScreen());
        assertFalse(r.canRemark());
        assertTrue(r.stages().stream().noneMatch(st -> st.canEdit()));
    }

    @Test
    void unfreezingRestoresTheActions() {
        candidate.getClient().setFrozen(true);

        assertThrows(PortalFrozenException.class,
                () -> service.screeningAction(100L,
                        new ScreeningActionRequest(ScreeningAction.ON_HOLD, "Wait"), recruiter));

        candidate.getClient().setFrozen(false);

        service.screeningAction(100L,
                new ScreeningActionRequest(ScreeningAction.ON_HOLD, "Wait"), recruiter);

        assertEquals(CandidateStatus.ON_HOLD, candidate.getStatus());
        assertTrue(service.getCandidate(100L, recruiter).canRemark());
    }

    @Test
    void permissionIsCheckedBeforeFreezeSoOutsidersLearnNothingAboutIt() {
        candidate.getClient().setFrozen(true);

        assertThrows(AccessDeniedException.class,
                () -> service.screeningAction(100L,
                        new ScreeningActionRequest(ScreeningAction.REJECT, "No"), hiringManager));
    }

    // ---------------------------------------------------------------- the new roles on candidates

    @Test
    void hrBpAndManagerCanOpenACandidateOfTheirPortalButNotScreenIt() {
        AuthUser hrbp = new AuthUser(8L, "hrbp@example.com", Role.HR_BP, 1L);
        AuthUser manager = new AuthUser(9L, "mgr@example.com", Role.MANAGER, 1L);

        for (AuthUser u : new AuthUser[]{hrbp, manager}) {
            CandidateResponse r = service.getCandidate(100L, u);

            assertEquals("Asha Rao", r.fullName());
            assertFalse(r.canScreen());

            assertThrows(AccessDeniedException.class,
                    () -> service.screeningAction(100L,
                            new ScreeningActionRequest(ScreeningAction.REJECT, "No"), u));
        }

        // A manager may add comments. An HR BP may not.
        assertFalse(service.getCandidate(100L, hrbp).canRemark());
        assertTrue(service.getCandidate(100L, manager).canRemark());
    }

    @Test
    void hrBpOfAnotherPortalCannotOpenTheCandidate() {
        AuthUser otherPortal = new AuthUser(8L, "hrbp@example.com", Role.HR_BP, 77L);

        assertThrows(AccessDeniedException.class,
                () -> service.getCandidate(100L, otherPortal));
    }

    // ---------------------------------------------------------------- helpers

    private void fillScreeningDetails() {
        candidate.setTotalExperience(new BigDecimal("5"));
        candidate.setCurrentCtc(new BigDecimal("800000"));
        candidate.setExpectedCtc(new BigDecimal("1200000"));
        candidate.setNoticePeriodDays(30);
        candidate.setSkills("Flutter, Dart");
    }

    /** The candidate must be exactly as before and nothing may have been written. */
    private void assertUntouched() {
        assertEquals(ScreeningStatus.PENDING, candidate.getScreeningStatus());
        assertEquals(CandidateStatus.ACTIVE, candidate.getStatus());
        assertTrue(savedEvents.isEmpty());
        assertTrue(savedRecords.isEmpty());
    }

    private List<CandidateEvent> eventsOfType(EventType type) {
        return savedEvents.stream().filter(e -> e.getType() == type).toList();
    }

    private static RecruitmentProcess process(Long id, String code, String name) {
        RecruitmentProcess p = new RecruitmentProcess(code, name, null);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private static ClientProcess link(Client client, RecruitmentProcess process, int order) {
        ClientProcess cp = new ClientProcess();
        cp.setClient(client);
        cp.setProcess(process);
        cp.setDisplayOrder(order);
        cp.setEnabled(true);
        return cp;
    }
}
