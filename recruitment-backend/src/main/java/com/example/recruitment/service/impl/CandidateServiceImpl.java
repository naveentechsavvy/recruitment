package com.example.recruitment.service.impl;

import com.example.recruitment.dto.CandidateResponse;
import com.example.recruitment.dto.CandidateResponse.EventView;
import com.example.recruitment.dto.CandidateResponse.StageView;
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
import com.example.recruitment.entity.ClientRoleAssignment;
import com.example.recruitment.entity.ClientStatus;
import com.example.recruitment.entity.EventType;
import com.example.recruitment.entity.RecruitmentProcess;
import com.example.recruitment.entity.ScreeningAction;
import com.example.recruitment.entity.Requirement;
import com.example.recruitment.entity.RequirementStatus;
import com.example.recruitment.entity.Role;
import com.example.recruitment.entity.ScreeningStatus;
import com.example.recruitment.entity.StageDecision;
import com.example.recruitment.exception.ResourceNotFoundException;
import com.example.recruitment.repository.CandidateEventRepository;
import com.example.recruitment.repository.CandidateRepository;
import com.example.recruitment.repository.CandidateStageRecordRepository;
import com.example.recruitment.repository.ClientProcessRepository;
import com.example.recruitment.repository.ClientRoleAssignmentRepository;
import com.example.recruitment.repository.ClientRepository;
import com.example.recruitment.repository.RequirementRepository;
import com.example.recruitment.security.AuthUser;
import com.example.recruitment.security.ClientAccess;
import com.example.recruitment.service.CandidateService;
import com.example.recruitment.service.OfferLetterService;
import com.example.recruitment.service.PortalGuard;
import com.example.recruitment.service.ResumeStorageService;
import com.example.recruitment.service.StagePermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class CandidateServiceImpl implements CandidateService {

    private static final String APPLICATION_STAGE = "candidate-application";

    /** Stages where a 1 to 5 rating is required when approving or rejecting. */
    private static final Set<String> RATED_STAGES = Set.of("technical-round", "technical-round-l1", "technical-round-l2");

    private final CandidateRepository candidates;
    private final CandidateStageRecordRepository records;
    private final CandidateEventRepository events;
    private final RequirementRepository requirements;
    private final ClientRepository clients;
    private final ClientProcessRepository clientProcesses;
    private final ResumeStorageService resumeStorage;
    private final StagePermissionService permissions;
    private final OfferLetterService offerLetters;
    private final ClientRoleAssignmentRepository roleAssignments;

    public CandidateServiceImpl(CandidateRepository candidates,
                                CandidateStageRecordRepository records,
                                CandidateEventRepository events,
                                RequirementRepository requirements,
                                ClientRepository clients,
                                ClientProcessRepository clientProcesses,
                                ResumeStorageService resumeStorage,
                                StagePermissionService permissions,
                                OfferLetterService offerLetters,
                                ClientRoleAssignmentRepository roleAssignments) {
        this.candidates = candidates;
        this.records = records;
        this.events = events;
        this.requirements = requirements;
        this.clients = clients;
        this.clientProcesses = clientProcesses;
        this.resumeStorage = resumeStorage;
        this.permissions = permissions;
        this.offerLetters = offerLetters;
        this.roleAssignments = roleAssignments;
    }

    // ---------------------------------------------------------------- creating candidates

    @Override
    public void apply(String slug, String jobId, String fullName, String email, String phone, MultipartFile resume) {
        Client client = clients.findBySlug(slug.trim().toLowerCase(Locale.ROOT))
                .filter(c -> c.getStatus() != ClientStatus.DRAFT)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
        PortalGuard.requireActive(client);

        Requirement requirement = requirements.findByClientIdAndJobId(client.getId(), jobId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));

        if (requirement.getStatus() != RequirementStatus.OPEN) {
            throw new IllegalArgumentException("This job is not accepting applications");
        }

        createCandidate(client, requirement, fullName, email, phone, resume, "Candidate", true);
    }

    @Override
    public CandidateResponse addCandidate(Long requirementId, String fullName, String email, String phone,
                                          MultipartFile resume, AuthUser user) {
        permissions.requireRecruiterOrAdmin(user);
        Requirement requirement = findRequirement(requirementId);
        ClientAccess.require(user, requirement.getClient().getId());
        PortalGuard.requireActive(requirement.getClient());

        if (requirement.getStatus() == RequirementStatus.CLOSED) {
            throw new IllegalArgumentException("This job is closed");
        }

        Candidate candidate = createCandidate(requirement.getClient(), requirement, fullName, email, phone,
                resume, user.email(), false);

        return toResponse(candidate, user, true);
    }

    private Candidate createCandidate(Client client, Requirement requirement, String fullName, String email,
                                      String phone, MultipartFile resume, String actor, boolean publicApply) {
        String name = clean(fullName);
        String mail = clean(email).toLowerCase(Locale.ROOT);
        String tel = clean(phone);

        if (name.isEmpty() || name.length() > 150) {
            throw new IllegalArgumentException("Please enter the full name");
        }

        if (mail.length() > 150 || !mail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("Please enter a valid email address");
        }

        if (tel.length() > 30) {
            throw new IllegalArgumentException("Phone number is too long");
        }

        if (candidates.existsByRequirementIdAndEmailIgnoreCase(requirement.getId(), mail)) {
            throw new IllegalArgumentException(publicApply
                    ? "You have already applied for this job"
                    : "A candidate with this email already exists for this job");
        }

        List<ClientProcess> stages = enabledStages(client.getId());

        if (stages.isEmpty()) {
            throw new IllegalArgumentException("This client has no recruitment stages yet");
        }

        ResumeStorageService.Stored stored = resumeStorage.save(resume);

        try {
            Candidate candidate = new Candidate();
            candidate.setClient(client);
            candidate.setRequirement(requirement);
            candidate.setFullName(name);
            candidate.setEmail(mail);
            candidate.setPhone(tel.isEmpty() ? null : tel);
            candidate.setResumeFile(stored.filename());
            candidate.setResumeOriginalName(stored.originalName());
            candidate.setCurrentProcess(stages.get(0).getProcess());

            candidates.save(candidate);

            if (publicApply) {
                log(candidate, EventType.APPLIED, "Applied online",
                        "Resume uploaded: " + stored.originalName(), actor);
            } else {
                log(candidate, EventType.RESUME_UPLOADED,
                        "Resume uploaded by recruiter", stored.originalName(), actor);
            }

            // Applying IS the "Candidate Application" stage, so it is done already.
            RecruitmentProcess first = stages.get(0).getProcess();

            if (APPLICATION_STAGE.equals(first.getCode())) {
                String note = publicApply ? "Applied online" : "Added by recruiter";

                saveRecord(candidate, first, StageDecision.APPROVED, note,
                        publicApply ? mail : actor);

                log(candidate, EventType.STAGE_DECISION,
                        first.getName() + ": Approved", note, "System");

                if (stages.size() > 1) {
                    RecruitmentProcess next = stages.get(1).getProcess();
                    candidate.setCurrentProcess(next);
                    log(candidate, EventType.STAGE_MOVED,
                            "Moved to " + next.getName(), null, "System");
                } else {
                    candidate.setStatus(CandidateStatus.COMPLETED);
                    log(candidate, EventType.COMPLETED,
                            "Recruitment process completed", null, "System");
                }
            }

            return candidate;
        } catch (RuntimeException e) {
            resumeStorage.delete(stored.filename());
            throw e;
        }
    }

    // ---------------------------------------------------------------- reading

    @Override
    @Transactional(readOnly = true)
    public List<CandidateResponse> getByRequirement(Long requirementId, AuthUser user) {
        Requirement requirement = findRequirement(requirementId);
        ClientAccess.require(user, requirement.getClient().getId());

        if (user != null && user.role() == Role.HIRING_MANAGER
                && !permissions.isOwner(user, requirement)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "This job is not assigned to you");
        }

        return candidates.findByRequirementIdOrderByCreatedAtDesc(requirementId).stream()
                .map(c -> toResponse(c, user, false))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CandidateResponse getCandidate(Long id, AuthUser user) {
        return toResponse(findAllowed(id, user), user, true);
    }

    @Override
    @Transactional(readOnly = true)
    public ResumeStorageService.Loaded getResume(Long id, AuthUser user) {
        Candidate candidate = findAllowed(id, user);

        if (candidate.getResumeFile() == null) {
            throw new ResourceNotFoundException("This candidate has no resume");
        }

        return resumeStorage.load(candidate.getResumeFile(), candidate.getResumeOriginalName());
    }

    /**
     * Replaces a candidate's resume. The new file is stored first; the old file is
     * removed only after the database transaction commits. If the transaction rolls
     * back, the newly uploaded file is removed instead.
     */
    @Override
    public CandidateResponse replaceResume(Long id, MultipartFile resume, AuthUser user) {
        permissions.requireRecruiterOrAdmin(user);
        Candidate candidate = findAllowed(id, user);
        PortalGuard.requireActive(candidate.getClient());

        if (resume == null || resume.isEmpty()) {
            throw new IllegalArgumentException("Please upload a resume.");
        }

        ResumeStorageService.Stored stored = resumeStorage.save(resume);
        String oldResumeFile = candidate.getResumeFile();

        try {
            candidate.setResumeFile(stored.filename());
            candidate.setResumeOriginalName(stored.originalName());

            Candidate saved = candidates.save(candidate);

            log(candidate, EventType.RESUME_UPLOADED,
                    "Resume replaced by recruiter", stored.originalName(), user.email());

            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            if (oldResumeFile != null && !oldResumeFile.isBlank()) {
                                resumeStorage.delete(oldResumeFile);
                            }
                        }

                        @Override
                        public void afterCompletion(int status) {
                            if (status != TransactionSynchronization.STATUS_COMMITTED) {
                                resumeStorage.delete(stored.filename());
                            }
                        }
                    });

            return toResponse(saved, user, true);
        } catch (RuntimeException e) {
            resumeStorage.delete(stored.filename());
            throw e;
        }
    }

    // ---------------------------------------------------------------- screening

    @Override
    public CandidateResponse updateScreening(Long id, ScreeningUpdateRequest request, AuthUser user) {
        permissions.requireRecruiterOrAdmin(user);
        Candidate c = findAllowed(id, user);
        PortalGuard.requireActive(c.getClient());

        if (c.getScreeningStatus() == ScreeningStatus.PROCEEDED) {
            throw new IllegalArgumentException(
                    "Screening is already completed, so the details are locked");
        }

        if (c.getScreeningStatus() == ScreeningStatus.REJECTED
                || c.getStatus() == CandidateStatus.REJECTED) {
            throw new IllegalArgumentException(
                    "This candidate has been rejected, so the details are locked");
        }

        List<String> changed = new ArrayList<>();

        if (!same(c.getTotalExperience(), request.totalExperience())) {
            c.setTotalExperience(request.totalExperience());
            changed.add("total experience");
        }

        if (!same(c.getRelevantExperience(), request.relevantExperience())) {
            c.setRelevantExperience(request.relevantExperience());
            changed.add("relevant experience");
        }

        if (!same(c.getCurrentCtc(), request.currentCtc())) {
            c.setCurrentCtc(request.currentCtc());
            changed.add("current CTC");
        }

        if (!same(c.getExpectedCtc(), request.expectedCtc())) {
            c.setExpectedCtc(request.expectedCtc());
            changed.add("expected CTC");
        }

        if (!Objects.equals(c.getNoticePeriodDays(), request.noticePeriodDays())) {
            c.setNoticePeriodDays(request.noticePeriodDays());
            changed.add("notice period");
        }

        String skills = clean(request.skills());
        String newSkills = skills.isEmpty() ? null : skills;

        if (!Objects.equals(c.getSkills(), newSkills)) {
            c.setSkills(newSkills);
            changed.add("skills");
        }

        if (!changed.isEmpty()) {
            log(c, EventType.SCREENING_UPDATED, "Screening details updated",
                    "Changed: " + String.join(", ", changed), user.email());
        }

        String remark = clean(request.remarks());

        if (!remark.isEmpty()) {
            log(c, EventType.REMARK, "Recruiter remark", remark, user.email());
        }

        return toResponse(c, user, true);
    }

    @Override
    public CandidateResponse screeningAction(Long id, ScreeningActionRequest request, AuthUser user) {
        permissions.requireRecruiterOrAdmin(user);
        Candidate c = findAllowed(id, user);
        PortalGuard.requireActive(c.getClient());

        if (c.getStatus() == CandidateStatus.COMPLETED) {
            throw new IllegalArgumentException("The recruitment process is already completed");
        }

        if (c.getScreeningStatus() == ScreeningStatus.PROCEEDED) {
            throw new IllegalArgumentException(
                    "Screening is already completed. Use the stage controls instead");
        }

        if (c.getScreeningStatus() == ScreeningStatus.REJECTED
                || c.getStatus() == CandidateStatus.REJECTED) {
            throw new IllegalArgumentException(
                    "This candidate has already been rejected and cannot be changed");
        }

        if (request.action() == ScreeningAction.ON_HOLD
                && (c.getScreeningStatus() == ScreeningStatus.ON_HOLD
                || c.getStatus() == CandidateStatus.ON_HOLD)) {
            throw new IllegalArgumentException("This candidate is already on hold");
        }

        String remarks = clean(request.remarks());
        String note = remarks.isEmpty() ? null : remarks;

        if (note == null && request.action() == ScreeningAction.REJECT) {
            throw new IllegalArgumentException(
                    "Please enter the reason for rejecting this candidate");
        }

        if (note == null && request.action() == ScreeningAction.ON_HOLD) {
            throw new IllegalArgumentException(
                    "Please enter the reason for putting this candidate on hold");
        }

        List<ClientProcess> stages = enabledStages(c.getClient().getId());
        int screeningIndex = indexOf(stages, StagePermissionService.SCREENING_STAGE);
        RecruitmentProcess current = c.getCurrentProcess();

        boolean atScreening = current != null
                && StagePermissionService.SCREENING_STAGE.equals(current.getCode());

        switch (request.action()) {
            case PROCEED -> {
                requireComplete(c);
                c.setScreeningStatus(ScreeningStatus.PROCEEDED);
                c.setStatus(CandidateStatus.ACTIVE);

                log(c, EventType.SCREENING_PROCEEDED,
                        "Screening completed: Proceed", note, user.email());

                if (screeningIndex >= 0 && atScreening) {
                    saveRecord(c, stages.get(screeningIndex).getProcess(),
                            StageDecision.APPROVED, note, user.email());

                    advance(c, stages, screeningIndex, user.email());
                }
            }

            case REJECT -> {
                c.setScreeningStatus(ScreeningStatus.REJECTED);
                c.setStatus(CandidateStatus.REJECTED);

                log(c, EventType.SCREENING_REJECTED,
                        "Screening completed: Reject", note, user.email());

                if (screeningIndex >= 0 && atScreening) {
                    saveRecord(c, stages.get(screeningIndex).getProcess(),
                            StageDecision.REJECTED, note, user.email());
                }
            }

            case ON_HOLD -> {
                c.setScreeningStatus(ScreeningStatus.ON_HOLD);
                c.setStatus(CandidateStatus.ON_HOLD);

                log(c, EventType.SCREENING_ON_HOLD,
                        "Screening: On hold", note, user.email());
            }
        }

        return toResponse(c, user, true);
    }

    @Override
    public CandidateResponse addRemark(Long id, RemarkRequest request, AuthUser user) {
        Candidate c = findAllowed(id, user);
        permissions.requireCanComment(user, c);
        PortalGuard.requireActive(c.getClient());

        String text = clean(request.text());
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Please enter a comment");
        }

        log(c, EventType.REMARK, permissions.remarkTitle(user), text, user.email());

        return toResponse(c, user, true);
    }

    // ---------------------------------------------------------------- stage decisions

    @Override
    public CandidateResponse updateStage(Long id, String stageCode,
                                         StageUpdateRequest request, AuthUser user) {
        Candidate candidate = findAllowed(id, user);

        permissions.requireCanEdit(user, candidate, stageCode); // 403 when not allowed, checked first
        PortalGuard.requireActive(candidate.getClient());

        if (candidate.getStatus() == CandidateStatus.REJECTED) {
            throw new IllegalArgumentException(
                    "This candidate has been rejected, so the stages can no longer be changed");
        }

        List<ClientProcess> stages = enabledStages(candidate.getClient().getId());
        int index = indexOf(stages, stageCode);

        if (index < 0) {
            throw new IllegalArgumentException(
                    "This stage is not part of the client's process");
        }

        RecruitmentProcess process = stages.get(index).getProcess();

        StageDecision decision = request.decision();
        String notes = clean(request.notes());

        if (notes.isEmpty()) {
            throw new IllegalArgumentException("Please enter a comment");
        }

        CandidateStageRecord existing = records
                .findByCandidateIdAndProcessId(candidate.getId(), process.getId())
                .orElse(null);
        StageDecision before = existing == null ? StageDecision.PENDING : existing.getDecision();
        boolean onHold = candidate.getStatus() == CandidateStatus.ON_HOLD;

        RecruitmentProcess current = candidate.getCurrentProcess();
        boolean isCurrent = current != null && current.getCode().equals(stageCode);

        // A finished stage stays finished: no second click, no change later.
        if (before == StageDecision.APPROVED || before == StageDecision.REJECTED) {
            throw new IllegalArgumentException("This stage is already "
                    + (before == StageDecision.APPROVED ? "approved" : "rejected")
                    + " and can no longer be changed");
        }

        // On hold: only the step that holds the candidate can act.
        // A screening hold is resumed from the Screening panel.
        if (onHold && before != StageDecision.HOLD) {
            throw new IllegalArgumentException(
                    candidate.getScreeningStatus() == ScreeningStatus.ON_HOLD
                            ? "This candidate is on hold. Use the screening Proceed action to continue"
                            : "This candidate is on hold at another step");
        }

        // Resume: PENDING is only valid for a candidate held at this very step.
        if (decision == StageDecision.PENDING) {
            if (!(onHold && before == StageDecision.HOLD)) {
                throw new IllegalArgumentException("Choose Approve, Reject or Hold");
            }

            // The stage keeps its rating, and the hold note stays in the history.
            saveRecord(candidate, process, StageDecision.PENDING, notes,
                    existing == null ? null : existing.getRating(), user.email());
            candidate.setStatus(CandidateStatus.ACTIVE);

            log(candidate, EventType.STAGE_DECISION,
                    process.getName() + ": Resumed", notes, user.email());

            return toResponse(candidate, user, true);
        }

        // Hold: the whole candidate waits at this step.
        if (decision == StageDecision.HOLD) {
            if (StagePermissionService.SCREENING_STAGE.equals(stageCode)
                    || APPLICATION_STAGE.equals(stageCode)) {
                throw new IllegalArgumentException(
                        "Use the Screening panel to put a candidate on hold at screening");
            }

            if (!isCurrent) {
                throw new IllegalArgumentException("Only the current step can be put on hold");
            }

            if (onHold) {
                throw new IllegalArgumentException("This candidate is already on hold");
            }

            // Only the role responsible for the stage may hold it, so the same role can resume it.
            permissions.requireCanHold(user, candidate, stageCode);

            saveRecord(candidate, process, StageDecision.HOLD, notes,
                    existing == null ? null : existing.getRating(), user.email());
            candidate.setStatus(CandidateStatus.ON_HOLD);

            log(candidate, EventType.STAGE_DECISION,
                    process.getName() + ": On hold", notes, user.email());

            return toResponse(candidate, user, true);
        }

        // Approve or Reject. The technical rounds also need a rating.
        Integer rating = null;

        if (RATED_STAGES.contains(stageCode)) {
            if (request.rating() == null) {
                throw new IllegalArgumentException("Please give a rating from 1 to 5");
            }
            rating = request.rating();
        }

        boolean approved = decision == StageDecision.APPROVED;

        // The screening stage is the recruiter's own result, so it follows
        // the same rules as the Screening panel.
        if (StagePermissionService.SCREENING_STAGE.equals(stageCode)
                && candidate.getScreeningStatus() != ScreeningStatus.PROCEEDED) {
            if (approved) {
                requireComplete(candidate);
                candidate.setScreeningStatus(ScreeningStatus.PROCEEDED);

                log(candidate, EventType.SCREENING_PROCEEDED,
                        "Screening completed: Proceed", notes, user.email());
            } else {
                candidate.setScreeningStatus(ScreeningStatus.REJECTED);

                log(candidate, EventType.SCREENING_REJECTED,
                        "Screening completed: Reject", notes, user.email());
            }
        }

        saveRecord(candidate, process, decision, notes, rating, user.email());

        String details = rating == null ? notes : "Rating: " + rating + "/5\n" + notes;

        log(candidate, EventType.STAGE_DECISION,
                process.getName() + ": " + (approved ? "Approved" : "Rejected"),
                details, user.email());

        // Only the current stage moves the candidate.
        if (isCurrent) {
            if (!approved) {
                candidate.setStatus(CandidateStatus.REJECTED);
            } else {
                candidate.setStatus(CandidateStatus.ACTIVE);
                advance(candidate, stages, index, user.email());

                // Approving Offer Released issues the offer: the letter is emailed to the candidate.
                // If the email fails the whole approval is undone, so the HR BP can simply try again.
                if (StagePermissionService.OFFER_RELEASE_STAGE.equals(stageCode)) {
                    sendOfferLetter(candidate, user.email());
                }
            }
        }

        return toResponse(candidate, user, true);
    }

    /** Emails the offer letter PDF to the candidate, with the job's hiring manager and the client's HR BPs in CC. */
    private void sendOfferLetter(Candidate candidate, String actor) {
        List<String> cc = new ArrayList<>();

        Requirement requirement = candidate.getRequirement();
        if (requirement != null && requirement.getHiringManager() != null) {
            cc.add(requirement.getHiringManager().getEmail());
        }

        if (candidate.getClient() != null) {
            roleAssignments.findByClientIdOrderByEmailAsc(candidate.getClient().getId()).stream()
                    .filter(a -> a.getRole() == Role.HR_BP)
                    .map(ClientRoleAssignment::getEmail)
                    .forEach(cc::add);
        }

        List<String> sentCc = offerLetters.sendOffer(candidate, cc);

        log(candidate, EventType.STAGE_DECISION, "Offer letter emailed",
                "To: " + candidate.getEmail() + (sentCc.isEmpty() ? "" : "\nCC: " + String.join(", ", sentCc)), actor);
    }

    /**
     * Moves the candidate to the next configured stage.
     * Nothing can be skipped, and nothing reaches the hiring manager unscreened.
     */
    private void advance(Candidate c, List<ClientProcess> stages, int index, String actor) {
        if (index + 1 < stages.size()) {
            RecruitmentProcess next = stages.get(index + 1).getProcess();

            if (StagePermissionService.HIRING_MANAGER_STAGE.equals(next.getCode())
                    && c.getScreeningStatus() != ScreeningStatus.PROCEEDED) {
                throw new IllegalArgumentException(
                        "Finish screening (Proceed) before forwarding the candidate to the hiring manager");
            }

            c.setCurrentProcess(next);
            c.setStatus(CandidateStatus.ACTIVE);

            log(c, EventType.STAGE_MOVED,
                    "Moved to " + next.getName(), null, actor);
        } else {
            c.setStatus(CandidateStatus.COMPLETED);

            log(c, EventType.COMPLETED,
                    "Recruitment process completed", null, actor);
        }
    }

    // ---------------------------------------------------------------- helpers

    private Requirement findRequirement(Long id) {
        return requirements.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Requirement not found with id " + id));
    }

    private Candidate findAllowed(Long id, AuthUser user) {
        Candidate candidate = candidates.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Candidate not found with id " + id));

        ClientAccess.require(user, candidate.getClient().getId());
        permissions.requireCanView(user, candidate);

        return candidate;
    }

    private List<ClientProcess> enabledStages(Long clientId) {
        return clientProcesses.findByClientIdOrderByDisplayOrderAsc(clientId).stream()
                .filter(ClientProcess::isEnabled)
                .toList();
    }

    private static int indexOf(List<ClientProcess> stages, String code) {
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).getProcess().getCode().equals(code)) {
                return i;
            }
        }

        return -1;
    }

    /**
     * The screening details that must be filled in before the candidate can proceed.
     * Relevant experience is optional.
     */
    private static List<String> missingFields(Candidate c) {
        List<String> missing = new ArrayList<>();

        if (c.getTotalExperience() == null) missing.add("Total experience");
        if (c.getCurrentCtc() == null) missing.add("Current CTC");
        if (c.getExpectedCtc() == null) missing.add("Expected CTC");
        if (c.getNoticePeriodDays() == null) missing.add("Notice period");
        if (c.getSkills() == null || c.getSkills().isBlank()) missing.add("Skills");

        return missing;
    }

    private static void requireComplete(Candidate c) {
        if (c.getResumeFile() == null || c.getResumeFile().isBlank()) {
            throw new IllegalArgumentException(
                    "Upload the candidate's resume before proceeding");
        }

        List<String> missing = missingFields(c);

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Complete the screening details first: " + String.join(", ", missing));
        }
    }

    private void saveRecord(Candidate candidate, RecruitmentProcess process,
                            StageDecision decision, String notes, String by) {
        saveRecord(candidate, process, decision, notes, null, by);
    }

    private void saveRecord(Candidate candidate, RecruitmentProcess process,
                            StageDecision decision, String notes, Integer rating, String by) {
        CandidateStageRecord record = records
                .findByCandidateIdAndProcessId(candidate.getId(), process.getId())
                .orElseGet(() -> {
                    CandidateStageRecord r = new CandidateStageRecord();
                    r.setCandidate(candidate);
                    r.setProcess(process);
                    return r;
                });

        record.setDecision(decision);
        record.setNotes(notes);
        record.setRating(rating);
        record.setUpdatedBy(by);
        record.setUpdatedAt(Instant.now());

        records.save(record);
    }

    private void log(Candidate candidate, EventType type,
                     String title, String details, String actor) {
        CandidateEvent e = new CandidateEvent();
        e.setCandidate(candidate);
        e.setType(type);
        e.setTitle(title);
        e.setDetails(details);
        e.setActor(actor);

        events.save(e);
    }

    private CandidateResponse toResponse(Candidate c, AuthUser user, boolean withDetail) {
        List<StageView> stageViews = new ArrayList<>();
        List<EventView> timeline = new ArrayList<>();

        if (withDetail) {
            Map<Long, CandidateStageRecord> byProcess =
                    records.findByCandidateId(c.getId()).stream()
                            .collect(Collectors.toMap(
                                    r -> r.getProcess().getId(),
                                    Function.identity()));

            String currentCode = c.getCurrentProcess() == null
                    ? null : c.getCurrentProcess().getCode();

            for (ClientProcess cp : enabledStages(c.getClient().getId())) {
                RecruitmentProcess p = cp.getProcess();
                CandidateStageRecord r = byProcess.get(p.getId());

                stageViews.add(new StageView(
                        p.getCode(),
                        p.getName(),
                        p.getDescription(),
                        r == null ? StageDecision.PENDING : r.getDecision(),
                        r == null ? null : r.getNotes(),
                        r == null ? null : r.getUpdatedBy(),
                        r == null ? null : r.getUpdatedAt(),
                        p.getCode().equals(currentCode),
                        !c.getClient().isFrozen()
                                && permissions.canEdit(user, c, p.getCode()),
                        r == null ? null : r.getRating()
                ));
            }

            // Only an admin (platform Admin or the client's Client Admin) sees the full history.
            // Everyone else gets the remarks only, which the Remarks card needs.
            boolean seesHistory = user != null
                    && (user.role() == Role.ADMIN || "CLIENT_ADMIN".equals(user.role().name()));

            for (CandidateEvent e : events.findByCandidateIdOrderByCreatedAtAscIdAsc(c.getId())) {
                if (seesHistory || e.getType() == EventType.REMARK) {
                    timeline.add(new EventView(
                            e.getId(),
                            e.getType(),
                            e.getTitle(),
                            e.getDetails(),
                            e.getActor(),
                            e.getCreatedAt()));
                }
            }
        }

        Requirement req = c.getRequirement();
        RecruitmentProcess current = c.getCurrentProcess();

        boolean recruiter = permissions.isRecruiterOrAdmin(user)
                && !c.getClient().isFrozen();

        boolean canScreen = recruiter
                && c.getScreeningStatus() != ScreeningStatus.PROCEEDED
                && c.getScreeningStatus() != ScreeningStatus.REJECTED
                && c.getStatus() != CandidateStatus.REJECTED
                && c.getStatus() != CandidateStatus.COMPLETED;

        boolean canRemark = !c.getClient().isFrozen() && permissions.canComment(user, c);

        return new CandidateResponse(
                c.getId(),
                c.getClient().getId(),
                req.getId(),
                req.getJobId(),
                req.getJobTitle(),
                c.getFullName(),
                c.getEmail(),
                c.getPhone(),
                c.getStatus(),
                c.getScreeningStatus(),
                current == null ? null : current.getCode(),
                current == null ? null : current.getName(),
                c.getResumeFile() != null,
                c.getResumeOriginalName(),
                c.getCreatedAt(),
                c.getTotalExperience(),
                c.getRelevantExperience(),
                c.getCurrentCtc(),
                c.getExpectedCtc(),
                c.getNoticePeriodDays(),
                c.getSkills(),
                missingFields(c),
                canScreen,
                canRemark,
                stageViews,
                timeline);
    }

    private static boolean same(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return a == b;
        return a.compareTo(b) == 0;
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }
}
