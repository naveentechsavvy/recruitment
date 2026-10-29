
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

@Component
public class StagePermissionService {

    public static final String HIRING_MANAGER_STAGE = "hiring-manager-review";
    public static final String SCREENING_STAGE = "resume-screening";
    public static final String OFFER_RELEASE_STAGE = "offer-released";
    public static final String MANAGER_ROUND_STAGE = "manager-round";

    // Manager is view-only.
    public static final Set<String> MANAGER_STAGES = Set.of();

    public static final Set<String> HR_BP_STAGES = Set.of(
            "hr-round",
            "compensation-discussion",
            "background-verification",
            OFFER_RELEASE_STAGE
    );

    /**
     * While a candidate is on hold at a stage, should an ADMIN still be able to resume, approve or reject it?
     * false = only the role responsible for the stage can (strict hold rules). Set to true to give admins an override.
     */
    public static final boolean ADMIN_CAN_ACT_ON_HOLD = false;

    /**
     * The one role responsible for a stage once it is on hold: it alone may put the stage on hold and resume it.
     * Hiring Manager Review belongs to the Hiring Manager, Offer Released to the HR BP, every other stage to the Recruiter.
     * (The front end keeps the same table in STEP_OWNER, site-candidate-details.component.ts.)
     */
    public Role holdOwner(String stageCode) {
        if (HIRING_MANAGER_STAGE.equals(stageCode)) {
            return Role.HIRING_MANAGER;
        }
        if (OFFER_RELEASE_STAGE.equals(stageCode)) {
            return Role.HR_BP;
        }
        return Role.RECRUITER;
    }

    private static String ownerLabel(Role role) {
        return switch (role) {
            case HIRING_MANAGER -> "hiring manager";
            case HR_BP -> "HR BP";
            default -> "recruiter";
        };
    }

    /** Only the role responsible for a stage may put it on hold, so that same role can always resume it. */
    public void requireCanHold(AuthUser user, Candidate candidate, String stageCode) {
        requireCanEdit(user, candidate, stageCode);

        if (user.role() != Role.ADMIN && user.role() != holdOwner(stageCode)) {
            throw new AccessDeniedException(
                    "Only the " + ownerLabel(holdOwner(stageCode)) + " can put this stage on hold");
        }
    }

    public boolean isRecruiterOrAdmin(AuthUser user) {
        return user != null
                && (user.role() == Role.ADMIN || user.role() == Role.RECRUITER);
    }

    public void requireRecruiterOrAdmin(AuthUser user) {
        if (!isRecruiterOrAdmin(user)) {
            throw new AccessDeniedException(
                    "Only a recruiter or admin can do this"
            );
        }
    }

    /** Checks whether the hiring manager owns the requirement. */
    public boolean isOwner(AuthUser user, Requirement requirement) {
        if (user == null || requirement == null
                || requirement.getClient() == null) {
            return false;
        }

        HiringManager owner = requirement.getHiringManager();

        return Objects.equals(
                    user.clientId(),
                    requirement.getClient().getId()
                )
                && owner != null
                && owner.getEmail() != null
                && owner.getEmail().equalsIgnoreCase(user.email());
    }

    /** Checks candidate visibility for hiring managers. */
    public void requireCanView(AuthUser user, Candidate candidate) {
        if (user == null || candidate == null) {
            throw new AccessDeniedException("Candidate access denied");
        }

        if (user.role() == Role.HIRING_MANAGER
                && !isOwner(user, candidate.getRequirement())) {
            throw new AccessDeniedException(
                    "This candidate is not assigned to you"
            );
        }

        if ((user.role() == Role.MANAGER || user.role() == Role.HR_BP)
                && (candidate.getClient() == null
                || !Objects.equals(
                        user.clientId(),
                        candidate.getClient().getId()
                ))) {
            throw new AccessDeniedException(
                    "You can only access your own client's data"
            );
        }
    }

    private static boolean isActive(Candidate candidate) {
        return candidate.getStatus() == CandidateStatus.ACTIVE;
    }

    /** Roles allowed to add candidate comments. */
    public boolean canComment(AuthUser user, Candidate candidate) {
        if (user == null || candidate == null) {
            return false;
        }

        return switch (user.role()) {
            case ADMIN, RECRUITER, MANAGER -> true;
            case HIRING_MANAGER ->
                    isOwner(user, candidate.getRequirement());
            default -> false;
        };
    }

    public void requireCanComment(AuthUser user, Candidate candidate) {
        if (!canComment(user, candidate)) {
            throw new AccessDeniedException(
                    "You cannot add comments to this candidate"
            );
        }
    }

    public String remarkTitle(AuthUser user) {
        if (user != null && user.role() == Role.HIRING_MANAGER) {
            return "Hiring manager comment";
        }
        if (user != null && user.role() == Role.MANAGER) {
            return "Manager comment";
        }
        return "Recruiter remark";
    }

    public boolean canEdit(
            AuthUser user,
            Candidate candidate,
            String stageCode
    ) {
        return denialReason(user, candidate, stageCode) == null;
    }

    public void requireCanEdit(
            AuthUser user,
            Candidate candidate,
            String stageCode
    ) {
        String reason = denialReason(user, candidate, stageCode);

        if (reason != null) {
            throw new AccessDeniedException(reason);
        }
    }

    /** Null means allowed; otherwise returns the denial reason. */
    private String denialReason(
            AuthUser user,
            Candidate candidate,
            String stageCode
    ) {
        if (user == null) {
            return "Please log in";
        }

        if (candidate == null || stageCode == null) {
            return "Candidate or stage is invalid";
        }

        // Held at a stage (after screening): only the role responsible for that stage can act. A screening hold
        // is not covered here, it is resumed from the Screening panel and CandidateService explains it.
        if (candidate.getStatus() == CandidateStatus.ON_HOLD
                && candidate.getScreeningStatus() == ScreeningStatus.PROCEEDED) {
            return holdDenialReason(user, candidate, stageCode);
        }

        if (user.role() == Role.ADMIN) {
            return null;
        }

        if (user.role() == Role.RECRUITER) {
            if (HIRING_MANAGER_STAGE.equals(stageCode)) {
                return "Only the hiring manager can edit the Hiring Manager Review stage";
            }

            if (OFFER_RELEASE_STAGE.equals(stageCode)) {
                return "Only an HR BP can release an offer";
            }

            return null;
        }

        if (user.role() == Role.MANAGER) {
            return "A manager has view-only access";
        }

        if (user.role() == Role.HR_BP) {
            return denialForStageOwner(user, candidate, stageCode);
        }

        if (user.role() != Role.HIRING_MANAGER) {
            return "You do not have permission to edit stages";
        }

        if (!HIRING_MANAGER_STAGE.equals(stageCode)) {
            return "Hiring managers can only edit the Hiring Manager Review stage";
        }

        if (candidate.getScreeningStatus() != ScreeningStatus.PROCEEDED) {
            return "The recruiter has not finished screening this candidate yet";
        }

        RecruitmentProcess current = candidate.getCurrentProcess();

        if (!isActive(candidate)
                || current == null
                || !HIRING_MANAGER_STAGE.equals(current.getCode())) {
            return "Hiring Manager Review can be edited only while it is the candidate's current stage";
        }

        if (!isOwner(user, candidate.getRequirement())) {
            return "Only the hiring manager of this job can edit this stage";
        }

        return null;
    }

    /**
     * The candidate is on hold at a stage. Nobody may touch any other stage. On the held stage only the role responsible
     * for it may resume, approve or reject: the Hiring Manager (owner of the job) for Hiring Manager Review, the Recruiter
     * for the other stages (the HR BP for Offer Released). Everyone else, a recruiter on the hiring manager's stage
     * and the other way round included, is refused. Null means allowed.
     */
    private String holdDenialReason(AuthUser user, Candidate candidate, String stageCode) {
        RecruitmentProcess current = candidate.getCurrentProcess();

        if (current == null || !stageCode.equals(current.getCode())) {
            return "This candidate is on hold. Only the held stage can be changed";
        }

        Role owner = holdOwner(stageCode);

        if (user.role() == Role.ADMIN) {
            return ADMIN_CAN_ACT_ON_HOLD
                    ? null
                    : "Only the " + ownerLabel(owner) + " can resume, approve or reject this stage";
        }

        if (user.role() != owner) {
            return "This candidate is on hold. Only the " + ownerLabel(owner)
                    + " can resume, approve or reject this stage";
        }

        if (owner == Role.HIRING_MANAGER && !isOwner(user, candidate.getRequirement())) {
            return "Only the hiring manager of this job can resume, approve or reject this stage";
        }

        if (candidate.getClient() == null
                || !Objects.equals(user.clientId(), candidate.getClient().getId())) {
            return "You can only access your own client's data";
        }

        return null;
    }

    /** HR BP may edit their stages only for active candidates after screening. */
    private String denialForStageOwner(
            AuthUser user,
            Candidate candidate,
            String stageCode
    ) {
        if (!HR_BP_STAGES.contains(stageCode)) {
            return "An HR BP can only edit the HR Round, Compensation Discussion, Background Verification and Offer Released stages";
        }

        if (candidate.getClient() == null
                || !Objects.equals(
                        user.clientId(),
                        candidate.getClient().getId()
                )) {
            return "You can only access your own client's data";
        }

        if (candidate.getScreeningStatus() != ScreeningStatus.PROCEEDED) {
            return "The recruiter has not finished screening this candidate yet";
        }

        RecruitmentProcess current = candidate.getCurrentProcess();

        if (!isActive(candidate)
                || current == null
                || !stageCode.equals(current.getCode())) {
            return "This stage can be edited only while it is the candidate's current stage";
        }

        return null;
    }
}
