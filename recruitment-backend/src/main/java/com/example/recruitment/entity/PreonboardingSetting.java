package com.example.recruitment.entity;

import jakarta.persistence.*;

/** One line of a client's pre-onboarding form: is this item asked for, and is it required? (table preonboarding_settings) */
@Entity
@Table(name = "preonboarding_settings",
        uniqueConstraints = @UniqueConstraint(name = "uk_preonb_setting", columnNames = {"client_id", "item_code"}))
public class PreonboardingSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    /** The name of a {@link PreonboardingItem}. Plain text so a new item never needs a table change. */
    @Column(name = "item_code", nullable = false, length = 40)
    private String itemCode;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private boolean mandatory;

    protected PreonboardingSetting() { }

    public PreonboardingSetting(Client client, String itemCode, boolean enabled, boolean mandatory) {
        this.client = client;
        this.itemCode = itemCode;
        this.enabled = enabled;
        this.mandatory = mandatory;
    }

    public Long getId() { return id; }
    public Client getClient() { return client; }
    public String getItemCode() { return itemCode; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }
}
