package com.example.recruitment.service;

import com.example.recruitment.dto.CandidateResponse;
import com.example.recruitment.dto.RemarkRequest;
import com.example.recruitment.dto.RequirementResponse;
import com.example.recruitment.dto.ScreeningActionRequest;
import com.example.recruitment.dto.ScreeningUpdateRequest;
import com.example.recruitment.dto.StageUpdateRequest;
import com.example.recruitment.entity.Candidate;
import com.example.recruitment.exception.ResourceNotFoundException;
import com.example.recruitment.repository.CandidateRepository;
import com.example.recruitment.security.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Candidate features for a client's OWN website (/api/site/...).
 *
 * 1. Client separation (permanent): the client always comes from the logged-in user. Every requirement or candidate
 *    is checked to belong to that client first. Anything of another client is answered with 404, as if it did not exist.
 *
 * 2. Roles: the logged-in person's REAL role is passed on. CandidateService and StagePermissionService decide
 *    who may screen, remark, add candidates and edit each stage (Manager is view-only, Hiring Manager edits only
 *    Hiring Manager Review, HR BP releases offers, Recruiter edits everything except releasing an offer).
 *
 * All hiring rules (frozen portal, locked screening, stage order, validation) stay in CandidateService, unchanged.
 */
@Service
@Transactional
public class SiteCandidateService {

    private final CandidateService candidates;
    private final RequirementService requirements;
    private final CandidateRepository candidateRepository;

    public SiteCandidateService(CandidateService candidates, RequirementService requirements,
                                CandidateRepository candidateRepository) {
        this.candidates = candidates;
        this.requirements = requirements;
        this.candidateRepository = candidateRepository;
    }

    @Transactional(readOnly = true)
    public List<CandidateResponse> list(AuthUser user, Long requirementId) {
        requireOwnRequirement(user, requirementId);
        return candidates.getByRequirement(requirementId, user);
    }

    public CandidateResponse add(AuthUser user, Long requirementId, String fullName, String email, String phone,
                                 MultipartFile resume) {
        requireOwnRequirement(user, requirementId);
        return candidates.addCandidate(requirementId, fullName, email, phone, resume, user);
    }

    @Transactional(readOnly = true)
    public CandidateResponse get(AuthUser user, Long id) {
        requireOwnCandidate(user, id);
        return candidates.getCandidate(id, user);
    }

    public CandidateResponse updateScreening(AuthUser user, Long id, ScreeningUpdateRequest request) {
        requireOwnCandidate(user, id);
        return candidates.updateScreening(id, request, user);
    }

    public CandidateResponse screeningAction(AuthUser user, Long id, ScreeningActionRequest request) {
        requireOwnCandidate(user, id);
        return candidates.screeningAction(id, request, user);
    }

    public CandidateResponse addRemark(AuthUser user, Long id, RemarkRequest request) {
        requireOwnCandidate(user, id);
        return candidates.addRemark(id, request, user);
    }

    public CandidateResponse updateStage(AuthUser user, Long id, String stageCode, StageUpdateRequest request) {
        requireOwnCandidate(user, id);
        return candidates.updateStage(id, stageCode, request, user);
    }

    @Transactional(readOnly = true)
    public ResumeStorageService.Loaded resume(AuthUser user, Long id) {
        requireOwnCandidate(user, id);
        return candidates.getResume(id, user);
    }

    public CandidateResponse replaceResume(AuthUser user, Long id, MultipartFile resume) {
        requireOwnCandidate(user, id);
        return candidates.replaceResume(id, resume, user);
    }

    // ------------------------------------------------------------------ helpers

    private static Long clientOf(AuthUser user) {
        if (user == null || user.clientId() == null) {
            throw new ResourceNotFoundException("No website is linked to this login");
        }
        return user.clientId();
    }

    private void requireOwnRequirement(AuthUser user, Long requirementId) {
        Long clientId = clientOf(user);
        RequirementResponse r = requirements.getRequirement(requirementId);
        if (!clientId.equals(r.clientId())) {
            throw new ResourceNotFoundException("Requirement not found with id " + requirementId);
        }
    }

    private void requireOwnCandidate(AuthUser user, Long candidateId) {
        Long clientId = clientOf(user);
        Candidate c = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found with id " + candidateId));
        if (!clientId.equals(c.getClient().getId())) {
            throw new ResourceNotFoundException("Candidate not found with id " + candidateId);
        }
    }
}