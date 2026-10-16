package com.example.recruitment.dto;

import com.example.recruitment.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Give this email a role on the portal. */
public record ClientRoleRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Please enter a valid email address")
        @Size(max = 150, message = "Email must be at most 150 characters")
        String email,

        @NotNull(message = "Please choose a role")
        Role role
) { }
