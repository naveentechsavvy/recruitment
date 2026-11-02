package com.example.recruitment.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * The pre-onboarding of one candidate who accepted the offer (table preonboarding_cases). Exactly one per candidate.
 * It is created automatically when the offer is accepted. It always belongs to a client, like everything else.
 */
@Entity
@Table(name = "preonboarding_cases", indexes = @Index(name = "idx_preonb_case_client", columnList = "client_id"))
public class PreonboardingCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false, unique = true)
    private Candidate candidate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    // Plain text column (not a fixed MySQL list), so a new status never needs a table change.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(30)")
    private PreonboardingStatus status = PreonboardingStatus.CREATED;

    /** The last day to submit. */
    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private long version;

    public Long getId() { return id; }
    public Candidate getCandidate() { return candidate; }
    public void setCandidate(Candidate candidate) { this.candidate = candidate; }
    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }
    public PreonboardingStatus getStatus() { return status; }
    public void setStatus(PreonboardingStatus status) { this.status = status; }
    public Instant getDueAt() { return dueAt; }
    public void setDueAt(Instant dueAt) { this.dueAt = dueAt; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
