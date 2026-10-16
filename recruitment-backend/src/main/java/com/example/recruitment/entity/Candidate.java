package com.example.recruitment.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/** One person's application for one job requirement. */
@Entity
@Table(name = "candidates",
        uniqueConstraints = @UniqueConstraint(name = "uk_candidate_requirement_email", columnNames = {"requirement_id", "email"}))
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requirement_id", nullable = false)
    private Requirement requirement;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    /** Random file name inside the private resume folder. */
    @Column(name = "resume_file", length = 100)
    private String resumeFile;

    @Column(name = "resume_original_name", length = 150)
    private String resumeOriginalName;

    /** The stage the candidate is waiting at right now. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_process_id")
    private RecruitmentProcess currentProcess;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CandidateStatus status = CandidateStatus.ACTIVE;

    // ---- recruiter screening details ----
    @Column(name = "total_experience", precision = 4, scale = 1)
    private BigDecimal totalExperience;

    @Column(name = "relevant_experience", precision = 4, scale = 1)
    private BigDecimal relevantExperience;

    @Column(name = "current_ctc", precision = 12, scale = 2)
    private BigDecimal currentCtc;

    @Column(name = "expected_ctc", precision = 12, scale = 2)
    private BigDecimal expectedCtc;

    @Column(name = "notice_period_days")
    private Integer noticePeriodDays;

    /** Comma-separated, e.g. "Java, Spring Boot". */
    @Column(length = 500)
    private String skills;

    /** columnDefinition gives old rows a value when the column is added to an existing table. */
    @Enumerated(EnumType.STRING)
    @Column(name = "screening_status", nullable = false, length = 20,
            columnDefinition = "varchar(20) not null default 'PENDING'")
    private ScreeningStatus screeningStatus = ScreeningStatus.PENDING;

    /** Stops two recruiters from saving over each other. Old rows start at 0. */
    @Version
    @Column(nullable = false, columnDefinition = "bigint not null default 0")
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() { return id; }
    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }
    public Requirement getRequirement() { return requirement; }
    public void setRequirement(Requirement requirement) { this.requirement = requirement; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getResumeFile() { return resumeFile; }
    public void setResumeFile(String resumeFile) { this.resumeFile = resumeFile; }
    public String getResumeOriginalName() { return resumeOriginalName; }
    public void setResumeOriginalName(String resumeOriginalName) { this.resumeOriginalName = resumeOriginalName; }
    public RecruitmentProcess getCurrentProcess() { return currentProcess; }
    public void setCurrentProcess(RecruitmentProcess currentProcess) { this.currentProcess = currentProcess; }
    public CandidateStatus getStatus() { return status; }
    public void setStatus(CandidateStatus status) { this.status = status; }
    public BigDecimal getTotalExperience() { return totalExperience; }
    public void setTotalExperience(BigDecimal totalExperience) { this.totalExperience = totalExperience; }
    public BigDecimal getRelevantExperience() { return relevantExperience; }
    public void setRelevantExperience(BigDecimal relevantExperience) { this.relevantExperience = relevantExperience; }
    public BigDecimal getCurrentCtc() { return currentCtc; }
    public void setCurrentCtc(BigDecimal currentCtc) { this.currentCtc = currentCtc; }
    public BigDecimal getExpectedCtc() { return expectedCtc; }
    public void setExpectedCtc(BigDecimal expectedCtc) { this.expectedCtc = expectedCtc; }
    public Integer getNoticePeriodDays() { return noticePeriodDays; }
    public void setNoticePeriodDays(Integer noticePeriodDays) { this.noticePeriodDays = noticePeriodDays; }
    public String getSkills() { return skills; }
    public void setSkills(String skills) { this.skills = skills; }
    public ScreeningStatus getScreeningStatus() { return screeningStatus; }
    public void setScreeningStatus(ScreeningStatus screeningStatus) { this.screeningStatus = screeningStatus; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
