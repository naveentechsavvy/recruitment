package com.example.recruitment.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Stored in MySQL as the constant name, sent to Angular as the readable label. */
public enum EmploymentType {
    FULL_TIME("Full Time"), PART_TIME("Part Time"), CONTRACT("Contract"), INTERNSHIP("Internship");

    private final String label;
    EmploymentType(String label) { this.label = label; }

    @JsonValue
    public String getLabel() { return label; }

    @JsonCreator
    public static EmploymentType fromLabel(String value) {
        for (EmploymentType s : values()) {
            if (s.label.equalsIgnoreCase(value) || s.name().equalsIgnoreCase(value)) return s;
        }
        throw new IllegalArgumentException("Invalid employment type: " + value);
    }
}
