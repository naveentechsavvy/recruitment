package com.example.recruitment.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** One line of a candidate's history. Rows are only ever added, never changed, so old remarks are never overwritten. */
@Entity
@Table(name = "candidate_events")
public class CandidateEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String details;

    /** Email of the user who did it, or "System" / "Candidate". */
    @Column(nullable = false, length = 150)
    private String actor;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Long getId() { return id; }
    public Candidate getCandidate() { return candidate; }
    public void setCandidate(Candidate candidate) { this.candidate = candidate; }
    public EventType getType() { return type; }
    public void setType(EventType type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public Instant getCreatedAt() { return createdAt; }
}
