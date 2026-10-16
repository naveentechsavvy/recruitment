package com.example.recruitment.controller;

import com.example.recruitment.service.CandidateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** Public (no login, see SecurityConfig): a candidate applies for one open job with a resume. */
@RestController
@RequestMapping("/api/public/clients")
public class PublicApplicationController {

    private final CandidateService candidateService;

    public PublicApplicationController(CandidateService candidateService) {
        this.candidateService = candidateService;
    }

    @PostMapping(value = "/{slug}/jobs/{jobId}/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> apply(@PathVariable String slug,
                                                     @PathVariable String jobId,
                                                     @RequestParam String fullName,
                                                     @RequestParam String email,
                                                     @RequestParam(required = false) String phone,
                                                     @RequestParam("resume") MultipartFile resume) {
        candidateService.apply(slug, jobId, fullName, email, phone, resume);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Application submitted"));
    }
}
