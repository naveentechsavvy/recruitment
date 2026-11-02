package com.example.recruitment.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * One offer letter sent to a candidate (table candidate_offers). A candidate can have several over time
 * (a resend replaces the older one), but only the newest can still be answered.
 * Only a SHA-256 hash of the secret link token is stored, never the token itself.
 */
@Entity
@Table(name = "candidate_offers", indexes = {
        @Index(name = "idx_offer_token_hash", columnList = "token_hash", unique = true),
        @Index(name = "idx_offer_candidate", columnList = "candidate_id")
})
public class CandidateOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    // Plain text column (not a fixed MySQL list), so a new status never needs a table change.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private OfferStatus status = OfferStatus.RELEASED;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "decline_reason", length = 500)
    private String declineReason;

    /** Email of the person who released or resent the offer. */
    @Column(name = "sent_by", length = 150)
    private String sentBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private long version;

    public Long getId() { return id; }
    public Candidate getCandidate() { return candidate; }
    public void setCandidate(Candidate candidate) { this.candidate = candidate; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public OfferStatus getStatus() { return status; }
    public void setStatus(OfferStatus status) { this.status = status; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getRespondedAt() { return respondedAt; }
    public void setRespondedAt(Instant respondedAt) { this.respondedAt = respondedAt; }
    public String getDeclineReason() { return declineReason; }
    public void setDeclineReason(String declineReason) { this.declineReason = declineReason; }
    public String getSentBy() { return sentBy; }
    public void setSentBy(String sentBy) { this.sentBy = sentBy; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
