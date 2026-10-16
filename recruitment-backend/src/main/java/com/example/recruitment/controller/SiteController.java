package com.example.recruitment.controller;

import com.example.recruitment.dto.SiteSettingsRequest;
import com.example.recruitment.dto.SiteSettingsResponse;
import com.example.recruitment.security.AuthUser;
import com.example.recruitment.service.SiteService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** The website editor's own endpoints. Only CLIENT_ADMIN can reach them (see SecurityConfig). */
@RestController
@RequestMapping("/api/site")
public class SiteController {

    private final SiteService service;

    public SiteController(SiteService service) {
        this.service = service;
    }

    @GetMapping("/settings")
    public SiteSettingsResponse get(@AuthenticationPrincipal AuthUser user) {
        return service.get(user);
    }

    @PutMapping("/settings")
    public SiteSettingsResponse update(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody SiteSettingsRequest request) {
        return service.update(user, request);
    }

    @PostMapping("/logo")
    public SiteSettingsResponse uploadLogo(@AuthenticationPrincipal AuthUser user, @RequestParam("file") MultipartFile file) {
        return service.uploadLogo(user, file);
    }

    @DeleteMapping("/logo")
    public SiteSettingsResponse removeLogo(@AuthenticationPrincipal AuthUser user) {
        return service.removeLogo(user);
    }
}