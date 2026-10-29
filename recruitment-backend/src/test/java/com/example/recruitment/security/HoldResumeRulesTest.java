package com.example.recruitment.security;

import com.example.recruitment.entity.Candidate;
import com.example.recruitment.entity.CandidateStatus;
import com.example.recruitment.entity.Client;
import com.example.recruitment.entity.HiringManager;
import com.example.recruitment.entity.RecruitmentProcess;
import com.example.recruitment.entity.Requirement;
import com.example.recruitment.entity.Role;
import com.example.recruitment.entity.ScreeningStatus;
import com.example.recruitment.service.StagePermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/** Hold and resume: only the role responsible for the held stage may resume, approve or reject it. */
class HoldResumeRulesTest {

    static final String HM_STAGE = StagePermissionService.HIRING_MANAGER_STAGE;

    final StagePermissionService permissions = new StagePermissionService();
    final AuthUser hiringManager = new AuthUser(1L, "hm@example.com", Role.HIRING_MANAGER, 5L);
    final AuthUser otherHiringManager = new AuthUser(2L, "other@example.com", Role.HIRING_MANAGER, 5L);
    final AuthUser recruiter = new AuthUser(3L, "r@example.com", Role.RECRUITER, 5L);
    final AuthUser hrbp = new AuthUser(4L, "h@example.com", Role.HR_BP, 5L);
    final AuthUser admin = new AuthUser(5L, "a@example.com", Role.ADMIN, 5L);
    final AuthUser manager = new AuthUser(6L, "m@example.com", Role.MANAGER, 5L);

    Candidate candidate;

    @BeforeEach
    void setUp() {
        Client client = new Client();
        ReflectionTestUtils.setField(client, "id", 5L);
        HiringManager owner = new HiringManager();
        owner.setEmail("hm@example.com");
        Requirement requirement = new Requirement();
        requirement.setClient(client);
        requirement.setHiringManager(owner);
        candidate = new Candidate();
        candidate.setClient(client);
        candidate.setRequirement(requirement);
        candidate.setScreeningStatus(ScreeningStatus.PROCEEDED);
        candidate.setStatus(CandidateStatus.ON_HOLD);
    }

    private void heldAt(String code) {
        candidate.setCurrentProcess(new RecruitmentProcess(code, code, null));
    }

    @Test
    void onlyTheHiringManagerOfTheJobCanResumeHiringManagerReview() {
        heldAt(HM_STAGE);
        assertTrue(permissions.canEdit(hiringManager, candidate, HM_STAGE));
        assertFalse(permissions.canEdit(recruiter, candidate, HM_STAGE));
        assertFalse(permissions.canEdit(otherHiringManager, candidate, HM_STAGE));
        assertFalse(permissions.canEdit(hrbp, candidate, HM_STAGE));
        assertFalse(permissions.canEdit(manager, candidate, HM_STAGE));
    }

    @Test
    void onlyTheRecruiterCanResumeAnOtherStage() {
        heldAt("technical-round-l1");
        assertTrue(permissions.canEdit(recruiter, candidate, "technical-round-l1"));
        assertFalse(permissions.canEdit(hiringManager, candidate, "technical-round-l1"));
        assertFalse(permissions.canEdit(hrbp, candidate, "technical-round-l1"));
    }

    @Test
    void onlyTheHrBpCanResumeOfferReleased() {
        heldAt(StagePermissionService.OFFER_RELEASE_STAGE);
        assertTrue(permissions.canEdit(hrbp, candidate, StagePermissionService.OFFER_RELEASE_STAGE));
        assertFalse(permissions.canEdit(recruiter, candidate, StagePermissionService.OFFER_RELEASE_STAGE));
    }

    @Test
    void nobodyCanTouchAStageThatIsNotTheHeldOne() {
        heldAt(HM_STAGE);
        assertFalse(permissions.canEdit(recruiter, candidate, "technical-round-l1"));
        assertFalse(permissions.canEdit(hiringManager, candidate, "technical-round-l1"));
    }

    @Test
    void adminIsBlockedWhileOnHold() {
        heldAt(HM_STAGE);
        assertEquals(StagePermissionService.ADMIN_CAN_ACT_ON_HOLD, permissions.canEdit(admin, candidate, HM_STAGE));
    }

    @Test
    void refusalNamesTheRoleThatCanResume() {
        heldAt(HM_STAGE);
        AccessDeniedException e = assertThrows(AccessDeniedException.class,
                () -> permissions.requireCanEdit(recruiter, candidate, HM_STAGE));
        assertTrue(e.getMessage().contains("hiring manager"));
    }

    @Test
    void onlyTheStageOwnerMayPutAStageOnHold() {
        candidate.setStatus(CandidateStatus.ACTIVE);
        heldAt("hr-round");
        assertDoesNotThrow(() -> permissions.requireCanHold(recruiter, candidate, "hr-round"));
        assertThrows(AccessDeniedException.class, () -> permissions.requireCanHold(hrbp, candidate, "hr-round"));
    }

    @Test
    void screeningHoldIsLeftToTheScreeningPanel() {
        candidate.setScreeningStatus(ScreeningStatus.ON_HOLD);
        heldAt(StagePermissionService.SCREENING_STAGE);
        assertTrue(permissions.canEdit(recruiter, candidate, StagePermissionService.SCREENING_STAGE));
    }
}
