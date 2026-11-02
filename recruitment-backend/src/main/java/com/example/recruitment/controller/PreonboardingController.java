package com.example.recruitment.controller;

import com.example.recruitment.dto.PreonboardingSettingsRequest;
import com.example.recruitment.dto.PreonboardingSettingsView;
import com.example.recruitment.security.AuthUser;
import com.example.recruitment.service.PreonboardingService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A client website's pre-onboarding settings. Admin, HR BP and Recruiter only (see SecurityConfig; the service checks again). */
@RestController
@RequestMapping("/api/site/preonboarding")
public class PreonboardingController {

    private final PreonboardingService service;

    public PreonboardingController(PreonboardingService service) {
        this.service = service;
    }

    @GetMapping("/settings")
    public PreonboardingSettingsView get(@AuthenticationPrincipal AuthUser user) {
        return service.getSettings(user);
    }

    @PutMapping("/settings")
    public PreonboardingSettingsView update(@AuthenticationPrincipal AuthUser user,
                                            @RequestBody PreonboardingSettingsRequest request) {
        return service.updateSettings(user, request);
    }
}
