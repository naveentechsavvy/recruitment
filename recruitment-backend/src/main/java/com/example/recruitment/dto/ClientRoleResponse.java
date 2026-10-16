package com.example.recruitment.dto;

import com.example.recruitment.entity.ClientRoleAssignment;
import com.example.recruitment.entity.Role;

import java.time.Instant;

/** hasAccount tells the admin whether this person already has a login. Without one the role starts to apply once they do. */
public record ClientRoleResponse(
        Long id,
        Long clientId,
        String email,
        Role role,
        String assignedBy,
        boolean hasAccount,
        Instant createdAt,
        Instant updatedAt
) {
    public static ClientRoleResponse from(ClientRoleAssignment a, boolean hasAccount) {
        return new ClientRoleResponse(a.getId(), a.getClientId(), a.getEmail(), a.getRole(), a.getAssignedBy(),
                hasAccount, a.getCreatedAt(), a.getUpdatedAt());
    }
}
