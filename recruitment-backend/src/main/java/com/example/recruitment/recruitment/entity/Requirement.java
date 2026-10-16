package com.example.recruitment.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** A job requirement. It belongs to one client and is owned by one hiring manager of that client. */
@Entity
@Table(name = "requirements",
        uniqueConstraints = @UniqueConstraint(name = "uk_requirement_client_job_id", columnNames = {"client_id", "job_id"}))
public class Requirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hiring_manager_id", nullable = false)
    private HiringManager hiringManager;

    @Column(name = "job_id", nullable = false, length = 50)
    private String jobId;

    @Column(name = "job_title", nullable = false, length = 150)
    private String jobTitle;

    @Column(name = "number_of_positions", nullable = false)
    private Integer numberOfPositions;

    @Column(name = "job_description", nullable = false, columnDefinition = "TEXT")
    private String jobDescription;

    /** Comma-separated, e.g. "Java, Spring Boot, Angular". */
    @Column(name = "required_skills", nullable = false, length = 500)
    private String requiredSkills;

    @Column(name = "min_experience", nullable = false)
    private Integer minExperience;

    @Column(name = "max_experience", nullable = false)
    private Integer maxExperience;

    @Column(nullable = false, length = 150)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 20)
    private EmploymentType employmentType = EmploymentType.FULL_TIME;

    @Column(length = 100)
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequirementPriority priority = RequirementPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequirementStatus status = RequirementStatus.OPEN;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() { return id; }
    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }
    public HiringManager getHiringManager() { return hiringManager; }
    public void setHiringManager(HiringManager hiringManager) { this.hiringManager = hiringManager; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
    public Integer getNumberOfPositions() { return numberOfPositions; }
    public void setNumberOfPositions(Integer numberOfPositions) { this.numberOfPositions = numberOfPositions; }
    public String getJobDescription() { return jobDescription; }
    public void setJobDescription(String jobDescription) { this.jobDescription = jobDescription; }
    public String getRequiredSkills() { return requiredSkills; }
    public void setRequiredSkills(String requiredSkills) { this.requiredSkills = requiredSkills; }
    public Integer getMinExperience() { return minExperience; }
    public void setMinExperience(Integer minExperience) { this.minExperience = minExperience; }
    public Integer getMaxExperience() { return maxExperience; }
    public void setMaxExperience(Integer maxExperience) { this.maxExperience = maxExperience; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public EmploymentType getEmploymentType() { return employmentType; }
    public void setEmploymentType(EmploymentType employmentType) { this.employmentType = employmentType; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public RequirementPriority getPriority() { return priority; }
    public void setPriority(RequirementPriority priority) { this.priority = priority; }
    public RequirementStatus getStatus() { return status; }
    public void setStatus(RequirementStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
