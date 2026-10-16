package com.example.recruitment.dto;

import com.example.recruitment.entity.ClientStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** What Angular sends to create or update a client. */
public record ClientRequest(
        @NotBlank(message = "Client name is required")
        @Size(max = 100, message = "Client name must be at most 100 characters")
        String name,

        @Size(max = 1000, message = "Description must be at most 1000 characters")
        String description,

        @Size(max = 100, message = "Application name must be at most 100 characters")
        String applicationName,

        /** A preset name, or a custom colour code such as #e11d48. */
        @Pattern(regexp = "default|navy|slate|indigo|teal|charcoal|royal-blue|blue-cyan|corporate|modern|red|orange|green|pink|#[0-9a-fA-F]{6}",
                message = "Theme must be a preset colour or a colour code like #e11d48")
        String theme,

        /** Optional. New clients default to Draft; updates keep the current status if omitted. */
        ClientStatus status,

        /**
         * Header colour of the public application, e.g. #1e40af (#RGB also works).
         * Left out (null) = keep what is saved. Empty ("") = clear it and use the theme's colour.
         */
        @Pattern(regexp = "^$|^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$",
                message = "Header colour must be a colour code like #1e40af")
        String headerColor,

        /** Footer colour. Same rules as headerColor. */
        @Pattern(regexp = "^$|^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$",
                message = "Footer colour must be a colour code like #111827")
        String footerColor,

        /** Footer details. Left out (null) = keep what is saved. When present it replaces all footer fields. */
        @Valid
        FooterRequest footer
) { }
