package com.example.recruitment.entity;

/**
 * Where a pre-onboarding case stands. Background Verification is NOT part of this: documents being approved
 * never means the background check has passed.
 */
public enum PreonboardingStatus {
    CREATED,
    INVITED,
    IN_PROGRESS,
    SUBMITTED,
    CORRECTION_REQUIRED,
    COMPLETED,
    EXPIRED
}
