package com.example.recruitment.dto;
import java.time.Instant;
public record PreonboardingDocumentView(Long id, String itemCode, String originalName, String contentType, long fileSize, String reviewStatus, String reviewComment, Instant uploadedAt, Instant reviewedAt, String reviewedBy) {}
