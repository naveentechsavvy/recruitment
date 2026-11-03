package com.example.recruitment.dto;
import java.util.List;
public record PreonboardingPublicView(String candidateName, String clientName, String status, String dueAt, boolean otpVerified, List<PreonboardingPublicItemView> items) {}
