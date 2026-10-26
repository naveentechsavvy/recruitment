package com.example.recruitment.controller;

import com.example.recruitment.dto.CandidateResponse;
import com.example.recruitment.dto.RemarkRequest;
import com.example.recruitment.dto.ScreeningActionRequest;
import com.example.recruitment.dto.ScreeningUpdateRequest;
import com.example.recruitment.dto.StageUpdateRequest;
import com.example.recruitment.security.AuthUser;
import com.example.recruitment.service.ResumeStorageService;
import com.example.recruitment.service.SiteCandidateService;
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
 * Candidates inside a client's OWN website. Same operations as CandidateController (Admin Portal), but under /api/site
 * and always limited to the logged-in user's own client. Who may call them is decided in SecurityConfig (/api/site/**).
 */
@RestController
@RequestMapping("/api/site")
public class SiteCandidateController {

    private final SiteCandidateService service;

    public SiteCandidateController(SiteCandidateService service) {
        this.service = service;
    }

    @GetMapping("/requirements/{requirementId}/candidates")
    public List<CandidateResponse> list(@PathVariable Long requirementId, @AuthenticationPrincipal AuthUser user) {
        return service.list(user, requirementId);
    }

    @PostMapping(value = "/requirements/{requirementId}/candidates", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CandidateResponse> add(@PathVariable Long requirementId,
                                                 @RequestParam String fullName,
                                                 @RequestParam String email,
                                                 @RequestParam(required = false) String phone,
                                                 @RequestParam("resume") MultipartFile resume,
                                                 @AuthenticationPrincipal AuthUser user) {
        CandidateResponse created = service.add(user, requirementId, fullName, email, phone, resume);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/candidates/{id}")
    public CandidateResponse getOne(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        return service.get(user, id);
    }

    @PutMapping("/candidates/{id}/screening")
    public CandidateResponse updateScreening(@PathVariable Long id, @Valid @RequestBody ScreeningUpdateRequest request,
                                             @AuthenticationPrincipal AuthUser user) {
        return service.updateScreening(user, id, request);
    }

    @PostMapping("/candidates/{id}/screening/action")
    public CandidateResponse screeningAction(@PathVariable Long id, @Valid @RequestBody ScreeningActionRequest request,
                                             @AuthenticationPrincipal AuthUser user) {
        return service.screeningAction(user, id, request);
    }

    @PostMapping("/candidates/{id}/remarks")
    public CandidateResponse addRemark(@PathVariable Long id, @Valid @RequestBody RemarkRequest request,
                                       @AuthenticationPrincipal AuthUser user) {
        return service.addRemark(user, id, request);
    }

    @PutMapping("/candidates/{id}/stages/{stageCode}")
    public CandidateResponse updateStage(@PathVariable Long id, @PathVariable String stageCode,
                                         @Valid @RequestBody StageUpdateRequest request,
                                         @AuthenticationPrincipal AuthUser user) {
        return service.updateStage(user, id, stageCode, request);
    }

    @GetMapping("/candidates/{id}/resume")
    public ResponseEntity<Resource> resume(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        ResumeStorageService.Loaded file = service.resume(user, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.downloadName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(file.resource());
    }

    @PutMapping(value = "/candidates/{id}/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CandidateResponse replaceResume(@PathVariable Long id,
                                           @RequestParam(value = "resume", required = false) MultipartFile resume,
                                           @AuthenticationPrincipal AuthUser user) {
        return service.replaceResume(user, id, resume);
    }
}