package com.example.recruitment.dto;

/** One item of the form. kind is DETAIL or DOCUMENT. */
public record PreonboardingItemView(String code, String kind, String label, boolean enabled, boolean mandatory) { }
