package com.example.recruitment.dto;

import com.example.recruitment.entity.EmploymentType;
import com.example.recruitment.entity.Requirement;
import com.example.recruitment.entity.RequirementPriority;
import com.example.recruitment.entity.RequirementStatus;

import java.time.Instant;

public record RequirementResponse(
        Long id,
        Long clientId,
        Long hiringManagerId,
        String hiringManagerName,
        String jobId,
        String jobTitle,
        Integer numberOfPositions,
        String jobDescription,
        String requiredSkills,
        Integer minExperience,
        Integer maxExperience,
        String location,
        EmploymentType employmentType,
        String department,
        RequirementPriority priority,
        RequirementStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static RequirementResponse from(Requirement r) {
        return new RequirementResponse(r.getId(), r.getClient().getId(), r.getHiringManager().getId(),
                r.getHiringManager().getName(), r.getJobId(), r.getJobTitle(), r.getNumberOfPositions(),
                r.getJobDescription(), r.getRequiredSkills(), r.getMinExperience(), r.getMaxExperience(),
                r.getLocation(), r.getEmploymentType(), r.getDepartment(), r.getPriority(), r.getStatus(),
                r.getCreatedAt(), r.getUpdatedAt());
    }
}
