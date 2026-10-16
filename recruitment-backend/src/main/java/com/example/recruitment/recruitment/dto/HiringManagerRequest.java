package com.example.recruitment.dto;

import com.example.recruitment.entity.HiringManagerStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** What Angular sends to create or update a hiring manager. */
public record HiringManagerRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 150, message = "Email must be at most 150 characters")
        String email,

        @Pattern(regexp = "^$|^[0-9+()\\-\\s]{6,20}$", message = "Enter a valid phone number")
        String phone,

        @Size(max = 100, message = "Department must be at most 100 characters")
        String department,

        /** Optional. New hiring managers default to Active; updates keep the current status if omitted. */
        HiringManagerStatus status
) { }
