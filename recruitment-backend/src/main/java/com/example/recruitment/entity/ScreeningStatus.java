package com.example.recruitment.entity;

/** The recruiter's screening result. Only PROCEEDED candidates may reach the hiring manager. */
public enum ScreeningStatus {
    PENDING,
    PROCEEDED,
    ON_HOLD,
    REJECTED
}
