package com.example.recruitment.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** A hiring manager belongs to exactly one client. */
@Entity
@Table(name = "hiring_managers",
        uniqueConstraints = @UniqueConstraint(name = "uk_hiring_manager_client_email", columnNames = {"client_id", "email"}))
public class HiringManager {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HiringManagerStatus status = HiringManagerStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() { return id; }
    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public HiringManagerStatus getStatus() { return status; }
    public void setStatus(HiringManagerStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
