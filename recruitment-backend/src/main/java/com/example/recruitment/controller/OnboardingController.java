package com.example.recruitment.controller;

import com.example.recruitment.dto.OnboardingCandidateView;
import com.example.recruitment.entity.PreonboardingStatus;
import com.example.recruitment.entity.Role;
import com.example.recruitment.repository.PreonboardingCaseRepository;
import com.example.recruitment.security.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/site/onboarding")
public class OnboardingController {

    private final PreonboardingCaseRepository repository;

    public OnboardingController(PreonboardingCaseRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<OnboardingCandidateView> getCompletedCandidates(
            @AuthenticationPrincipal AuthUser user
    ) {
        if (user == null ||
                (user.role() != Role.ADMIN
                        && user.role() != Role.HR_BP
                        && user.role() != Role.RECRUITER)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        if (user.clientId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        return repository
                .findByClientIdAndStatusOrderByCreatedAtDesc(
                        user.clientId(),
                        PreonboardingStatus.COMPLETED
                )
                .stream()
                .map(c -> new OnboardingCandidateView(
                        c.getCandidate().getId(),
                        c.getCandidate().getFullName(),
                        c.getCandidate().getEmail(),
                        c.getCandidate().getPhone(),
                        c.getCandidate().getRequirement().getId(),
                        c.getCandidate().getStatus().name(),
                        c.getStatus().name(),
                        c.getCreatedAt()
                ))
                .toList();
    }
}
