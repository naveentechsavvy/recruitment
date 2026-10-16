package com.example.recruitment.entity;

import jakarta.persistence.*;

import java.time.Instant;

/** The decision and notes for one candidate at one stage. A stage with no row yet is simply "pending". */
@Entity
@Table(name = "candidate_stage_records",
        uniqueConstraints = @UniqueConstraint(name = "uk_candidate_stage", columnNames = {"candidate_id", "process_id"}))
public class CandidateStageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "process_id", nullable = false)
    private RecruitmentProcess process;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StageDecision decision = StageDecision.PENDING;

    @Column(length = 1000)
    private String notes;

    /** Email of the person who last changed this stage. */
    @Column(name = "updated_by", length = 150)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public Candidate getCandidate() { return candidate; }
    public void setCandidate(Candidate candidate) { this.candidate = candidate; }
    public RecruitmentProcess getProcess() { return process; }
    public void setProcess(RecruitmentProcess process) { this.process = process; }
    public StageDecision getDecision() { return decision; }
    public void setDecision(StageDecision decision) { this.decision = decision; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
