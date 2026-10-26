package com.example.recruitment.security;

import com.example.recruitment.entity.Candidate;
import com.example.recruitment.entity.CandidateStatus;
import com.example.recruitment.entity.Client;
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

/** What the Manager and HR BP roles may do, next to the existing roles. */
class StagePermissionRolesTest {

    final StagePermissionService permissions = new StagePermissionService();
    final AuthUser manager = new AuthUser(1L, "m@example.com", Role.MANAGER, 5L);
    final AuthUser hrbp = new AuthUser(2L, "h@example.com", Role.HR_BP, 5L);
    final AuthUser otherPortalHrbp = new AuthUser(3L, "o@example.com", Role.HR_BP, 8L);
    final AuthUser recruiter = new AuthUser(4L, "r@example.com", Role.RECRUITER, 5L);

    Candidate candidate;

    @BeforeEach
    void setUp() {
        Client client = new Client();
        ReflectionTestUtils.setField(client, "id", 5L);
        Requirement requirement = new Requirement();
        requirement.setClient(client);
        candidate = new Candidate();
        candidate.setClient(client);
        candidate.setRequirement(requirement);
        candidate.setScreeningStatus(ScreeningStatus.PROCEEDED);
        candidate.setStatus(CandidateStatus.ACTIVE);
        atStage("hr-round");
    }

    private void atStage(String code) {
        candidate.setCurrentProcess(new RecruitmentProcess(code, code, null));
    }

    @Test
    void managerAndHrBpCanViewEveryCandidateOfTheirPortal() {
        assertDoesNotThrow(() -> permissions.requireCanView(manager, candidate));
        assertDoesNotThrow(() -> permissions.requireCanView(hrbp, candidate));
    }

    @Test
    void hrBpCanEditTheHrStagesWhileTheyAreCurrent() {
        for (String stage : StagePermissionService.HR_BP_STAGES) {
            atStage(stage);
            assertTrue(permissions.canEdit(hrbp, candidate, stage), stage);
        }
    }

    @Test
    void hrBpCannotEditOtherStages() {
        atStage("technical-round-l1");
        assertFalse(permissions.canEdit(hrbp, candidate, "technical-round-l1"));
        assertFalse(permissions.canEdit(hrbp, candidate, StagePermissionService.SCREENING_STAGE));
        assertFalse(permissions.canEdit(hrbp, candidate, StagePermissionService.HIRING_MANAGER_STAGE));
    }

    @Test
    void managerIsViewOnly() {
        for (String stage : new String[]{"manager-round", "hr-round", "offer-released", StagePermissionService.HIRING_MANAGER_STAGE}) {
            atStage(stage);
            assertFalse(permissions.canEdit(manager, candidate, stage), stage);
        }
        assertDoesNotThrow(() -> permissions.requireCanView(manager, candidate));
    }

    @Test
    void hrBpCanReleaseTheOffer() {
        atStage("offer-released");
        assertTrue(permissions.canEdit(hrbp, candidate, "offer-released"));
    }

    @Test
    void recruiterCanEditEverythingExceptReleasingTheOffer() {
        atStage("offer-released");
        assertFalse(permissions.canEdit(recruiter, candidate, "offer-released"));
        assertThrows(AccessDeniedException.class, () -> permissions.requireCanEdit(recruiter, candidate, "offer-released"));
        for (String stage : new String[]{StagePermissionService.SCREENING_STAGE, "technical-round-l1", "manager-round", "offer-approval", "offer-accepted"}) {
            atStage(stage);
            assertTrue(permissions.canEdit(recruiter, candidate, stage), stage);
        }
    }

    @Test
    void adminCanReleaseTheOffer() {
        atStage("offer-released");
        AuthUser admin = new AuthUser(10L, "admin@example.com", Role.ADMIN, null);
        assertTrue(permissions.canEdit(admin, candidate, "offer-released"));
    }

    @Test
    void aStageThatIsNotTheCurrentOneCannotBeEdited() {
        atStage("technical-round-l1");
        assertFalse(permissions.canEdit(hrbp, candidate, "hr-round"));
    }

    @Test
    void nothingCanBeEditedBeforeScreeningIsFinishedOrWhenNotActive() {
        candidate.setScreeningStatus(ScreeningStatus.PENDING);
        assertFalse(permissions.canEdit(hrbp, candidate, "hr-round"));
        candidate.setScreeningStatus(ScreeningStatus.PROCEEDED);
        candidate.setStatus(CandidateStatus.ON_HOLD);
        assertFalse(permissions.canEdit(hrbp, candidate, "hr-round"));
        candidate.setStatus(CandidateStatus.REJECTED);
        assertFalse(permissions.canEdit(hrbp, candidate, "hr-round"));
    }

    @Test
    void anotherPortalsHrBpCannotEdit() {
        assertFalse(permissions.canEdit(otherPortalHrbp, candidate, "hr-round"));
        assertThrows(AccessDeniedException.class, () -> permissions.requireCanEdit(otherPortalHrbp, candidate, "hr-round"));
    }

    @Test
    void managerAndHrBpCannotRunRecruiterActions() {
        assertThrows(AccessDeniedException.class, () -> permissions.requireRecruiterOrAdmin(manager));
        assertThrows(AccessDeniedException.class, () -> permissions.requireRecruiterOrAdmin(hrbp));
        assertDoesNotThrow(() -> permissions.requireRecruiterOrAdmin(recruiter));
    }

    @Test
    void theNewRolesAreLimitedToTheirOwnPortal() {
        assertTrue(ClientAccess.isLimitedToOneClient(manager));
        assertTrue(ClientAccess.isLimitedToOneClient(hrbp));
        assertThrows(AccessDeniedException.class, () -> ClientAccess.require(hrbp, 8L));
        assertDoesNotThrow(() -> ClientAccess.require(hrbp, 5L));
        assertFalse(ClientAccess.isLimitedToOneClient(new AuthUser(9L, "a@example.com", Role.ADMIN, null)));
    }
}
