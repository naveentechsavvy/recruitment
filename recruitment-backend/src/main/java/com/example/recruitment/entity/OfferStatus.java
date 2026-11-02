package com.example.recruitment.entity;

/**
 * Where one offer letter stands. This is separate from the candidate's own status.
 * RELEASED = sent, waiting for the candidate. EXPIRED is not stored: a RELEASED offer whose date has passed counts as expired.
 * WITHDRAWN = cancelled by HR, or replaced by a newer offer.
 */
public enum OfferStatus {
    RELEASED,
    ACCEPTED,
    DECLINED,
    EXPIRED,
    WITHDRAWN
}
