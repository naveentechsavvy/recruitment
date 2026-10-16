
package com.example.recruitment.controller;

import com.example.recruitment.dto.CandidateResponse;
import com.example.recruitment.dto.RemarkRequest;
import com.example.recruitment.dto.ScreeningActionRequest;
import com.example.recruitment.dto.ScreeningUpdateRequest;
import com.example.recruitment.dto.StageUpdateRequest;
import com.example.recruitment.security.AuthUser;
import com.example.recruitment.service.CandidateService;
import com.example.recruitment.service.ResumeStorageService;

import jakarta.validation.Valid;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Candidate management APIs for the admin portal.
 * Authorization is handled by Spring Security and the service layer.
 */
@RestController
@RequestMapping("/api")
public class CandidateController {

    private final CandidateService candidateService;

    public CandidateController(CandidateService candidateService) {
        this.candidateService = candidateService;
    }

    /**
     * Get all candidates belonging to a requirement.
     */
    @GetMapping("/requirements/{requirementId}/candidates")
    public List<CandidateResponse> getByRequirement(
            @PathVariable Long requirementId,
            @AuthenticationPrincipal AuthUser me) {

        return candidateService.getByRequirement(requirementId, me);
    }

    /**
     * Create a candidate and upload their resume.
     * Recruiter or admin access is controlled by the security/service layer.
     */
    @PostMapping(
            value = "/requirements/{requirementId}/candidates",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<CandidateResponse> add(
            @PathVariable Long requirementId,
            @RequestParam String fullName,
            @RequestParam String email,
            @RequestParam(required = false) String phone,
            @RequestParam("resume") MultipartFile resume,
            @AuthenticationPrincipal AuthUser me) {

        CandidateResponse created = candidateService.addCandidate(
                requirementId,
                fullName,
                email,
                phone,
                resume,
                me
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Get candidate details.
     */
    @GetMapping("/candidates/{id}")
    public CandidateResponse getOne(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthUser me) {

        return candidateService.getCandidate(id, me);
    }

    /**
     * Update candidate screening information.
     */
    @PutMapping("/candidates/{id}/screening")
    public CandidateResponse updateScreening(
            @PathVariable Long id,
            @Valid @RequestBody ScreeningUpdateRequest request,
            @AuthenticationPrincipal AuthUser me) {

        return candidateService.updateScreening(id, request, me);
    }

    /**
     * Proceed, reject, or put a candidate on hold.
     */
    @PostMapping("/candidates/{id}/screening/action")
    public CandidateResponse screeningAction(
            @PathVariable Long id,
            @Valid @RequestBody ScreeningActionRequest request,
            @AuthenticationPrincipal AuthUser me) {

        return candidateService.screeningAction(id, request, me);
    }

    /**
     * Add a remark to a candidate.
     */
    @PostMapping("/candidates/{id}/remarks")
    public CandidateResponse addRemark(
            @PathVariable Long id,
            @Valid @RequestBody RemarkRequest request,
            @AuthenticationPrincipal AuthUser me) {

        return candidateService.addRemark(id, request, me);
    }

    /**
     * Update a candidate's stage.
     * The service layer must enforce the user's stage permissions.
     */
    @PutMapping("/candidates/{id}/stages/{stageCode}")
    public CandidateResponse updateStage(
            @PathVariable Long id,
            @PathVariable String stageCode,
            @Valid @RequestBody StageUpdateRequest request,
            @AuthenticationPrincipal AuthUser me) {

        return candidateService.updateStage(id, stageCode, request, me);
    }

    /**
     * Download a candidate's resume.
     */
    @GetMapping("/candidates/{id}/resume")
    public ResponseEntity<Resource> resume(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthUser me) {

        ResumeStorageService.Loaded file =
                candidateService.getResume(id, me);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mediaType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(
                                        file.downloadName(),
                                        StandardCharsets.UTF_8
                                )
                                .build()
                                .toString()
                )
                .header("X-Content-Type-Options", "nosniff")
                .body(file.resource());
    }
}

