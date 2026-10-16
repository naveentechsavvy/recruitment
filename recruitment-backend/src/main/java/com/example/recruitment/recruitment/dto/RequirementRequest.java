package com.example.recruitment.dto;

import com.example.recruitment.entity.EmploymentType;
import com.example.recruitment.entity.RequirementPriority;
import com.example.recruitment.entity.RequirementStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** What Angular sends to create or update a requirement. The client comes from the URL, not the body. */
public record RequirementRequest(
        @NotBlank(message = "Job title is required")
        @Size(max = 150, message = "Job title must be at most 150 characters")
        String jobTitle,

        @NotBlank(message = "Job ID is required")
        @Size(max = 50, message = "Job ID must be at most 50 characters")
        String jobId,

        @NotNull(message = "Number of positions is required")
        @Min(value = 1, message = "Number of positions must be greater than 0")
        Integer numberOfPositions,

        @NotBlank(message = "Job description is required")
        @Size(max = 5000, message = "Job description must be at most 5000 characters")
        String jobDescription,

        @NotBlank(message = "Required skills are required")
        @Size(max = 500, message = "Required skills must be at most 500 characters")
        String requiredSkills,

        @NotNull(message = "Minimum experience is required")
        @Min(value = 0, message = "Minimum experience cannot be negative")
        @Max(value = 60, message = "Minimum experience must be at most 60 years")
        Integer minExperience,

        @NotNull(message = "Maximum experience is required")
        @Min(value = 0, message = "Maximum experience cannot be negative")
        @Max(value = 60, message = "Maximum experience must be at most 60 years")
        Integer maxExperience,

        @NotBlank(message = "Location is required")
        @Size(max = 150, message = "Location must be at most 150 characters")
        String location,

        /** Optional. Defaults to Full Time. */
        EmploymentType employmentType,

        @Size(max = 100, message = "Department must be at most 100 characters")
        String department,

        /** Optional. Defaults to Medium. */
        RequirementPriority priority,

        /** Optional. New requirements default to Open; updates keep the current status if omitted. */
        RequirementStatus status,

        @NotNull(message = "Hiring manager is required")
        Long hiringManagerId
) { }
