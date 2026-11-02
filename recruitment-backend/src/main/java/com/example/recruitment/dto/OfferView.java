package com.example.recruitment.dto;

import com.example.recruitment.entity.OfferStatus;

import java.time.Instant;

/** The offer as the hiring team sees it on the candidate page. The server decides canResend and canWithdraw. */
public record OfferView(
        Long id,
        OfferStatus status,
        Instant sentAt,
        Instant expiresAt,
        Instant respondedAt,
        String declineReason,
        String sentBy,
        boolean canResend,
        boolean canWithdraw
) { }
