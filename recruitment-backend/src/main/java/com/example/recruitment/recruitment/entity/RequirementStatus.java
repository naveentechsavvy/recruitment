package com.example.recruitment.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Stored in MySQL as the constant name, sent to Angular as the readable label. */
public enum RequirementStatus {
    OPEN("Open"), ON_HOLD("On Hold"), CLOSED("Closed");

    private final String label;
    RequirementStatus(String label) { this.label = label; }

    @JsonValue
    public String getLabel() { return label; }

    @JsonCreator
    public static RequirementStatus fromLabel(String value) {
        for (RequirementStatus s : values()) {
            if (s.label.equalsIgnoreCase(value) || s.name().equalsIgnoreCase(value)) return s;
        }
        throw new IllegalArgumentException("Invalid requirement status: " + value);
    }
}
