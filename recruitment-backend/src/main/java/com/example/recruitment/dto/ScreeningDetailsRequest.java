package com.example.recruitment.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

/** The screening details a recruiter enters. Blank text fields are stored as empty. */
public record ScreeningDetailsRequest(
        @DecimalMin(value = "0", message = "Total experience cannot be negative")
        @DecimalMax(value = "60", message = "Total experience must be 60 years or less")
        Double totalExperience,

        @DecimalMin(value = "0", message = "Relevant experience cannot be negative")
        @DecimalMax(value = "60", message = "Relevant experience must be 60 years or less")
        Double relevantExperience,

        @Size(max = 50, message = "Current CTC must be at most 50 characters") String currentCtc,
        @Size(max = 50, message = "Expected CTC must be at most 50 characters") String expectedCtc,
        @Size(max = 50, message = "Notice period must be at most 50 characters") String noticePeriod,
        @Size(max = 500, message = "Skills must be at most 500 characters") String skills
) { }
