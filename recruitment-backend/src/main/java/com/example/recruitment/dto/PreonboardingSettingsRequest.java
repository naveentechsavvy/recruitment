package com.example.recruitment.dto;

import java.util.List;

/** Items that are left out of the list stay as they were. */
public record PreonboardingSettingsRequest(int submissionDays, List<PreonboardingItemRequest> items) { }
