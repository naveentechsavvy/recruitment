package com.example.recruitment.dto;

import com.example.recruitment.entity.HiringManager;
import com.example.recruitment.entity.HiringManagerStatus;

import java.time.Instant;

public record HiringManagerResponse(
        Long id,
        Long clientId,
        String name,
        String email,
        String phone,
        String department,
        HiringManagerStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static HiringManagerResponse from(HiringManager h) {
        return new HiringManagerResponse(h.getId(), h.getClient().getId(), h.getName(), h.getEmail(),
                h.getPhone(), h.getDepartment(), h.getStatus(), h.getCreatedAt(), h.getUpdatedAt());
    }
}
