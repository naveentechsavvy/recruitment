package com.example.recruitment.dto;

import com.example.recruitment.entity.CandidateStatus;
import com.example.recruitment.entity.EventType;
import com.example.recruitment.entity.ScreeningStatus;
import com.example.recruitment.entity.StageDecision;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** One candidate. "stages" and "timeline" are empty in list responses and filled in the single-candidate response. */
public record CandidateResponse(
        Long id,
        Long clientId,
        Long requirementId,
        String jobId,
        String jobTitle,
        String fullName,
        String email,
        String phone,
        CandidateStatus status,
        ScreeningStatus screeningStatus,
        String currentStageCode,
        String currentStageName,
        boolean hasResume,
        String resumeName,
        Instant appliedAt,
        BigDecimal totalExperience,
        BigDecimal relevantExperience,
        BigDecimal currentCtc,
        BigDecimal expectedCtc,
        Integer noticePeriodDays,
        String skills,
        List<String> missingScreeningFields,
        boolean canScreen,
        boolean canRemark,
        List<StageView> stages,
        List<EventView> timeline
) {
    /** canEdit is decided by the server for the logged-in user, so the screen never has to guess. */
    public record StageView(
            String code,
            String name,
            String description,
            StageDecision decision,
            String notes,
            String updatedBy,
            Instant updatedAt,
            boolean current,
            boolean canEdit,
            Integer rating
    ) { }

    public record EventView(
            Long id,
            EventType type,
            String title,
            String details,
            String actor,
            Instant at
    ) { }
}
