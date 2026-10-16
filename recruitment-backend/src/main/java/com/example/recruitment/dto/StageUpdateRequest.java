package com.example.recruitment.dto;

import com.example.recruitment.entity.StageDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StageUpdateRequest(
        @NotNull StageDecision decision,
        @Size(max = 1000) String notes) { }
