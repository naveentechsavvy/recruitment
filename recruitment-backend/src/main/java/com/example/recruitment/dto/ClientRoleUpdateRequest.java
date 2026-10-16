package com.example.recruitment.dto;

import com.example.recruitment.entity.Role;
import jakarta.validation.constraints.NotNull;

/** Change the role of an existing assignment. The email cannot be changed: remove it and add a new one. */
public record ClientRoleUpdateRequest(
        @NotNull(message = "Please choose a role")
        Role role
) { }
