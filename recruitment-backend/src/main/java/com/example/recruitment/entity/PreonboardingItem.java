package com.example.recruitment.entity;

import java.util.Optional;

/**
 * The fixed list of things a client can ask a new joiner for. The client chooses which are used and which are
 * required (a settings page), but cannot invent new ones. Keeping the list fixed keeps the first version simple and safe.
 */
public enum PreonboardingItem {

    DATE_OF_BIRTH(Kind.DETAIL, "Date of birth", true),
    CURRENT_ADDRESS(Kind.DETAIL, "Current address", true),
    PERMANENT_ADDRESS(Kind.DETAIL, "Permanent address", false),
    EMERGENCY_CONTACT_NAME(Kind.DETAIL, "Emergency contact name", true),
    EMERGENCY_CONTACT_PHONE(Kind.DETAIL, "Emergency contact phone", true),

    ID_PROOF(Kind.DOCUMENT, "Aadhaar / ID proof", true),
    PAN_CARD(Kind.DOCUMENT, "PAN card", true),
    EDUCATION_CERTIFICATE(Kind.DOCUMENT, "Education certificate", true),
    EXPERIENCE_LETTER(Kind.DOCUMENT, "Experience letter", false),
    RELIEVING_LETTER(Kind.DOCUMENT, "Relieving letter", false),
    BANK_DETAILS(Kind.DOCUMENT, "Bank details (passbook or cancelled cheque)", true),
    PASSPORT_PHOTO(Kind.DOCUMENT, "Passport size photo", false);

    public enum Kind { DETAIL, DOCUMENT }

    private final Kind kind;
    private final String label;
    private final boolean defaultMandatory;

    PreonboardingItem(Kind kind, String label, boolean defaultMandatory) {
        this.kind = kind;
        this.label = label;
        this.defaultMandatory = defaultMandatory;
    }

    public Kind getKind() { return kind; }
    public String getLabel() { return label; }
    public boolean isDefaultMandatory() { return defaultMandatory; }

    public static Optional<PreonboardingItem> find(String code) {
        if (code == null) return Optional.empty();
        for (PreonboardingItem item : values()) {
            if (item.name().equals(code.trim())) return Optional.of(item);
        }
        return Optional.empty();
    }
}
