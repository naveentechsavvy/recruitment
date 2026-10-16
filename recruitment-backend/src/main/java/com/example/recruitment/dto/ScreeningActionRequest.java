package com.example.recruitment.dto;

import com.example.recruitment.entity.ScreeningAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ScreeningActionRequest(
        @NotNull ScreeningAction action,
        @Size(max = 1000) String remarks) { }
