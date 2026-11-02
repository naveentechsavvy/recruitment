package com.example.recruitment.dto;

import com.example.recruitment.entity.PreonboardingStatus;

import java.time.Instant;

/** The case as the hiring team sees it on the candidate page. */
public record PreonboardingCaseView(Long id, PreonboardingStatus status, Instant createdAt, Instant dueAt) { }
