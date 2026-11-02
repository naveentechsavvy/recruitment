package com.example.recruitment.entity;

import jakarta.persistence.*;

/** The other pre-onboarding setting of a client: how many days a new joiner has to submit. One row per client. */
@Entity
@Table(name = "preonboarding_config")
public class PreonboardingConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false, unique = true)
    private Client client;

    @Column(name = "submission_days", nullable = false)
    private int submissionDays;

    protected PreonboardingConfig() { }

    public PreonboardingConfig(Client client, int submissionDays) {
        this.client = client;
        this.submissionDays = submissionDays;
    }

    public Long getId() { return id; }
    public Client getClient() { return client; }
    public int getSubmissionDays() { return submissionDays; }
    public void setSubmissionDays(int submissionDays) { this.submissionDays = submissionDays; }
}
