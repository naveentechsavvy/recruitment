package com.example.recruitment.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** The details the recruiter collects while screening. A remark is added to the history, not stored over an old one. */
public record ScreeningUpdateRequest(
        @DecimalMin("0") @DecimalMax("80") BigDecimal totalExperience,
        @DecimalMin("0") @DecimalMax("80") BigDecimal relevantExperience,
        @DecimalMin("0") @DecimalMax("9999999999") BigDecimal currentCtc,
        @DecimalMin("0") @DecimalMax("9999999999") BigDecimal expectedCtc,
        @Min(0) @Max(365) Integer noticePeriodDays,
        @Size(max = 500) String skills,
        @Size(max = 1000) String remarks) { }
