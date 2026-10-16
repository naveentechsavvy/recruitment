
package com.example.recruitment.controller;

import com.example.recruitment.dto.CandidateResponse;
import com.example.recruitment.security.AuthUser;
import com.example.recruitment.service.CandidateService;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Candidate screening-related file operations.
 *
 * Candidate creation, screening updates, screening actions,
 * and remarks are handled by CandidateController.
 */
@RestController
@RequestMapping("/api")
public class CandidateScreeningController {

    private final CandidateService candidateService;

    public CandidateScreeningController(
            CandidateService candidateService) {
        this.candidateService = candidateService;
    }

    /**
     * Replace a candidate's existing resume.
     */
    @PutMapping(
            value = "/candidates/{id}/resume",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public CandidateResponse replaceResume(
            @PathVariable Long id,
            @RequestParam(value = "resume", required = false)
            MultipartFile resume,
            @AuthenticationPrincipal AuthUser me) {

        return candidateService.replaceResume(id, resume, me);
    }
}

