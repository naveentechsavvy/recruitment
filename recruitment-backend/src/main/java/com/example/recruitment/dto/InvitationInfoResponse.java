package com.example.recruitment.dto;

/** What the set-password page shows: who is being invited and to which client. */
public record InvitationInfoResponse(String email, String clientName, String logoUrl, String clientSlug) { }