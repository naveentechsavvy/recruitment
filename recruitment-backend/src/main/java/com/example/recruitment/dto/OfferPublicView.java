package com.example.recruitment.dto;

import com.example.recruitment.entity.OfferStatus;

import java.time.Instant;

/** What the candidate sees on the offer page. Nothing about other candidates, other jobs or the hiring team. */
public record OfferPublicView(
        String candidateName,
        String jobTitle,
        String companyName,
        OfferStatus status,
        Instant expiresAt,
        Instant respondedAt,
        boolean canRespond
) { }
