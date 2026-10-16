package com.example.recruitment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** What a client sends to edit their own website. Only page content: no colours, theme or stages. */
public record SiteSettingsRequest(
        @NotBlank(message = "Application name is required")
        @Size(max = 100, message = "Application name must be at most 100 characters")
        String applicationName,

        @Size(max = 1000, message = "Description must be at most 1000 characters")
        String description,

        /** The whole footer. Blank fields are cleared, so this must always be sent. */
        @NotNull(message = "Footer details are required")
        @Valid
        FooterRequest footer
) { }