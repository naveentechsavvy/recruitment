package com.example.recruitment.dto;

import com.example.recruitment.entity.AppUser;
import com.example.recruitment.entity.Role;

import java.time.Instant;

public record UserResponse(Long id, String email, Role role, Long clientId, boolean active, Instant createdAt) {
    public static UserResponse from(AppUser u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getRole(), u.getClientId(), u.isActive(), u.getCreatedAt());
    }
}
