package com.example.recruitment.dto;

import com.example.recruitment.entity.EmploymentType;
import com.example.recruitment.entity.Requirement;
import com.example.recruitment.entity.RequirementPriority;

import java.time.Instant;

/** An open job as shown on the public page. The hiring manager's details are NOT included. */
public record PublicJobResponse(
        Long id,
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
        Instant createdAt
) {
    public static PublicJobResponse from(Requirement r) {
        return new PublicJobResponse(r.getId(), r.getJobId(), r.getJobTitle(), r.getNumberOfPositions(),
                r.getJobDescription(), r.getRequiredSkills(), r.getMinExperience(), r.getMaxExperience(),
                r.getLocation(), r.getEmploymentType(), r.getDepartment(), r.getPriority(), r.getCreatedAt());
    }
}
