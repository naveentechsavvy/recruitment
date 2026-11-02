package com.example.recruitment.entity;

/**
 * ACTIVE = moving through the stages. ON_HOLD = paused by the recruiter. REJECTED = stopped. COMPLETED = passed the last stage.
 * OFFERED = the offer letter has been sent. ACCEPTED = the candidate accepted it. DECLINED = the candidate turned it down (final).
 * OFFERED and ACCEPTED are still "in progress": the remaining stages (for example Background Verification) can be worked on.
 */
public enum CandidateStatus {
    ACTIVE,
    ON_HOLD,
    REJECTED,
    COMPLETED,
    OFFERED,
    ACCEPTED,
    DECLINED;

    /** The candidate is moving through the process (not paused, stopped or finished). */
    public boolean inProgress() {
        return this == ACTIVE || this == OFFERED || this == ACCEPTED;
    }
}
