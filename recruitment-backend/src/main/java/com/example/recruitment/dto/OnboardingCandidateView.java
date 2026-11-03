package com.example.recruitment.dto;

import java.time.Instant;

public record OnboardingCandidateView(
        Long candidateId,
        String fullName,
        String email,
        String phone,
        Long requirementId,
        String candidateStatus,
        String onboardingStatus,
        Instant caseCreatedAt
) {
}
