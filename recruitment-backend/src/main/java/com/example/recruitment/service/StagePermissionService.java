package com.example.recruitment.service;

import com.example.recruitment.entity.Candidate;
import com.example.recruitment.entity.CandidateStatus;
import com.example.recruitment.entity.HiringManager;
import com.example.recruitment.entity.RecruitmentProcess;
import com.example.recruitment.entity.Requirement;
import com.example.recruitment.entity.Role;
import com.example.recruitment.entity.ScreeningStatus;
import com.example.recruitment.security.AuthUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

/**
 * THE rule for who may do what with a candidate. Nothing else in the app decides this.
 *  - ADMIN and RECRUITER: may screen candidates and edit every stage.
 *  - HIRING_MANAGER: may only VIEW candidates of jobs he owns, and may edit ONLY "hiring-manager-review",
 *    ONLY while it is the candidate's current stage and ONLY after the recruiter has finished screening.
 *  - MANAGER: sees every candidate of his portal and may edit ONLY "manager-round" (same current-stage rules).
 *  - HR_BP: sees every candidate of his portal and may edit ONLY the HR stages: "hr-round",
 *    "compensation-discussion" and "background-verification" (same current-stage rules).
 *  Nobody can edit a stage of a frozen portal: PortalGuard blocks it in the services.
 */
@Component
public class StagePermissionService {

    public static final String HIRING_MANAGER_STAGE = "hiring-manager-review";
    public static final String SCREENING_STAGE = "resume-screening";
    public static final Set<String> MANAGER_STAGES = Set.of("manager-round");
    public static final Set<String> HR_BP_STAGES = Set.of("hr-round", "compensation-discussion", "background-verification");

    public boolean isRecruiterOrAdmin(AuthUser user) {
        return user != null && (user.role() == Role.ADMIN || user.role() == Role.RECRUITER);
    }

    public void requireRecruiterOrAdmin(AuthUser user) {
        if (!isRecruiterOrAdmin(user)) throw new AccessDeniedException("Only a recruiter or admin can do this");
    }

    /** True when this hiring manager is the one assigned to the job (same client and same email). */
    public boolean isOwner(AuthUser user, Requirement requirement) {
        if (user == null || requirement == null) return false;
        HiringManager owner = requirement.getHiringManager();
        return Objects.equals(user.clientId(), requirement.getClient().getId())
                && owner != null && owner.getEmail() != null && owner.getEmail().equalsIgnoreCase(user.email());
    }

    /** A hiring manager may only open candidates of his own jobs. */
    public void requireCanView(AuthUser user, Candidate candidate) {
        if (user != null && user.role() == Role.HIRING_MANAGER && !isOwner(user, candidate.getRequirement())) {
            throw new AccessDeniedException("This candidate is not assigned to you");
        }
    }

    public boolean canEdit(AuthUser user, Candidate candidate, String stageCode) {
        return denialReason(user, candidate, stageCode) == null;
    }

    /** Throws AccessDeniedException (answered as HTTP 403) when the user may not edit this stage. */
    public void requireCanEdit(AuthUser user, Candidate candidate, String stageCode) {
        String reason = denialReason(user, candidate, stageCode);
        if (reason != null) throw new AccessDeniedException(reason);
    }

    /** Null means allowed; otherwise the reason it is not. */
    private String denialReason(AuthUser user, Candidate candidate, String stageCode) {
        if (user == null) return "Please log in";
        if (isRecruiterOrAdmin(user)) return null;
        if (user.role() == Role.MANAGER || user.role() == Role.HR_BP) return denialForStageOwner(user, candidate, stageCode);
        if (user.role() != Role.HIRING_MANAGER) return "You do not have permission to edit stages";

        if (!HIRING_MANAGER_STAGE.equals(stageCode)) {
            return "Hiring managers can only edit the Hiring Manager Review stage";
        }
        if (candidate.getScreeningStatus() != ScreeningStatus.PROCEEDED) {
            return "The recruiter has not finished screening this candidate yet";
        }
        RecruitmentProcess current = candidate.getCurrentProcess();
        if (candidate.getStatus() != CandidateStatus.ACTIVE
                || current == null || !HIRING_MANAGER_STAGE.equals(current.getCode())) {
            return "Hiring Manager Review can be edited only while it is the candidate's current stage";
        }
        if (!isOwner(user, candidate.getRequirement())) {
            return "Only the hiring manager of this job can edit this stage";
        }
        return null;
    }

    /** Manager and HR BP: only their own stages, only while the stage is current and screening is finished. */
    private String denialForStageOwner(AuthUser user, Candidate candidate, String stageCode) {
        boolean manager = user.role() == Role.MANAGER;
        Set<String> allowed = manager ? MANAGER_STAGES : HR_BP_STAGES;
        if (!allowed.contains(stageCode)) {
            return manager ? "A manager can only edit the Manager Round stage"
                    : "An HR BP can only edit the HR Round, Compensation Discussion and Background Verification stages";
        }
        if (!Objects.equals(user.clientId(), candidate.getClient().getId())) {
            return "You can only access your own client's data";
        }
        if (candidate.getScreeningStatus() != ScreeningStatus.PROCEEDED) {
            return "The recruiter has not finished screening this candidate yet";
        }
        RecruitmentProcess current = candidate.getCurrentProcess();
        if (candidate.getStatus() != CandidateStatus.ACTIVE || current == null || !stageCode.equals(current.getCode())) {
            return "This stage can be edited only while it is the candidate's current stage";
        }
        return null;
    }
}
