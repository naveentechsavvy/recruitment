package com.example.recruitment.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** A stage the platform offers, e.g. "Technical Round". Same list for every client. */
@Entity
@Table(name = "recruitment_processes")
public class RecruitmentProcess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable identifier used by the Angular app, e.g. "technical-round". */
    @Column(nullable = false, unique = true, length = 60)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RecruitmentProcess() { } // required by JPA

    public RecruitmentProcess(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
}
