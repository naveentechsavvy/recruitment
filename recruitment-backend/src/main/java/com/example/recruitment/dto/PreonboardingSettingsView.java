package com.example.recruitment.dto;

import java.util.List;

public record PreonboardingSettingsView(int submissionDays, List<PreonboardingItemView> items) { }
