package com.example.recruitment.dto;

import com.example.recruitment.entity.RequirementStatus;
import jakarta.validation.constraints.NotNull;

/** Body of PATCH /api/requirements/{id}/status. */
public record RequirementStatusRequest(
        @NotNull(message = "Status is required")
        RequirementStatus status
) { }
