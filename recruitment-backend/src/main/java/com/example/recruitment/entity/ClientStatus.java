package com.example.recruitment.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Stored in MySQL as DRAFT/READY/ACTIVE, sent to Angular as "Draft"/"Ready"/"Active". */
public enum ClientStatus {
    DRAFT("Draft"), READY("Ready"), ACTIVE("Active");

    private final String label;
    ClientStatus(String label) { this.label = label; }

    @JsonValue
    public String getLabel() { return label; }

    @JsonCreator
    public static ClientStatus fromLabel(String value) {
        for (ClientStatus s : values()) {
            if (s.label.equalsIgnoreCase(value) || s.name().equalsIgnoreCase(value)) return s;
        }
        throw new IllegalArgumentException("Invalid status: " + value);
    }
}
