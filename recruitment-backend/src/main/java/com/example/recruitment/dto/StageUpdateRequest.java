package com.example.recruitment.dto;

import com.example.recruitment.entity.StageDecision;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * decision: APPROVED, REJECTED, HOLD (or PENDING to resume a candidate who is on hold at this stage).
 * notes: the comment, required for every action.
 * rating: 1 to 5, required by the service for the technical rounds when approving or rejecting.
 */
public record StageUpdateRequest(
        @NotNull StageDecision decision,
        @NotBlank(message = "Please enter a comment.") @Size(max = 1000) String notes,
        @Min(value = 1, message = "Rating must be between 1 and 5.")
        @Max(value = 5, message = "Rating must be between 1 and 5.") Integer rating) { }
