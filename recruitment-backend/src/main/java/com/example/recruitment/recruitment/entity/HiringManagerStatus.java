package com.example.recruitment.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Stored in MySQL as the constant name, sent to Angular as the readable label. */
public enum HiringManagerStatus {
    ACTIVE("Active"), INACTIVE("Inactive");

    private final String label;
    HiringManagerStatus(String label) { this.label = label; }

    @JsonValue
    public String getLabel() { return label; }

    @JsonCreator
    public static HiringManagerStatus fromLabel(String value) {
        for (HiringManagerStatus s : values()) {
            if (s.label.equalsIgnoreCase(value) || s.name().equalsIgnoreCase(value)) return s;
        }
        throw new IllegalArgumentException("Invalid hiring manager status: " + value);
    }
}
