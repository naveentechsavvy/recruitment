package com.example.recruitment.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Stored in MySQL as the constant name, sent to Angular as the readable label. */
public enum RequirementPriority {
    LOW("Low"), MEDIUM("Medium"), HIGH("High"), CRITICAL("Critical");

    private final String label;
    RequirementPriority(String label) { this.label = label; }

    @JsonValue
    public String getLabel() { return label; }

    @JsonCreator
    public static RequirementPriority fromLabel(String value) {
        for (RequirementPriority s : values()) {
            if (s.label.equalsIgnoreCase(value) || s.name().equalsIgnoreCase(value)) return s;
        }
        throw new IllegalArgumentException("Invalid priority: " + value);
    }
}
