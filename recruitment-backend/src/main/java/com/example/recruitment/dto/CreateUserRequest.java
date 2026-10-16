package com.example.recruitment.dto;

import com.example.recruitment.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** clientId is required when role is HIRING_MANAGER and ignored for the other roles. */
public record CreateUserRequest(
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(min = 6, max = 72) String password,
        @NotNull Role role,
        Long clientId) { }
