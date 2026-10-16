package com.example.recruitment.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * "This email has this role on this client portal." One row per email AND client, so the same person
 * can have a different role on each portal. At login, RoleResolver picks the row for the portal
 * the person signed in through. The assignment can be created before the person has a login, and it
 * starts to apply as soon as an account with that email exists.
 */
@Entity
@Table(name = "client_role_assignments",
        uniqueConstraints = @UniqueConstraint(name = "uk_role_assignment_email_client", columnNames = {"email", "client_id"}))
public class ClientRoleAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    /** Always stored in lower case. */
    @Column(nullable = false, length = 150)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /** Email of the admin who made or last changed the assignment. */
    @Column(name = "assigned_by", nullable = false, length = 150)
    private String assignedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() { return id; }
    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public String getAssignedBy() { return assignedBy; }
    public void setAssignedBy(String assignedBy) { this.assignedBy = assignedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}