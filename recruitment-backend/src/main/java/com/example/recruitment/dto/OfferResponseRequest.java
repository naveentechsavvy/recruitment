package com.example.recruitment.dto;

/** decision is ACCEPT or DECLINE. reason is optional and only used for a decline. */
public record OfferResponseRequest(String decision, String reason) { }
