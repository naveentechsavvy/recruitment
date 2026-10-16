package com.example.recruitment.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "clients")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 1000)
    private String description;

    /** URL-friendly unique name used by the public application, e.g. "samsung" -> /app/samsung. */
    @Column(length = 100, unique = true)
    private String slug;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "application_name", length = 100)
    private String applicationName;

    @Column(nullable = false, length = 20)
    private String theme = "default";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClientStatus status = ClientStatus.DRAFT;

    // ---- freeze: a frozen portal keeps all its data but accepts no new applications or changes (see PortalGuard) ----
    @Column(nullable = false, columnDefinition = "tinyint(1) not null default 0")
    private boolean frozen = false;

    @Column(name = "frozen_at")
    private Instant frozenAt;

    @Column(name = "frozen_by", length = 150)
    private String frozenBy;

    // ---- look of the public application: null means "use the theme's default" ----
    @Column(name = "header_color", length = 7)
    private String headerColor;

    @Column(name = "footer_color", length = 7)
    private String footerColor;

    // ---- footer details of the public application, all optional ----
    @Column(name = "footer_company_name", length = 150)
    private String footerCompanyName;

    @Column(name = "footer_address", length = 300)
    private String footerAddress;

    @Column(name = "footer_email", length = 150)
    private String footerEmail;

    @Column(name = "footer_phone", length = 30)
    private String footerPhone;

    @Column(name = "footer_website", length = 200)
    private String footerWebsite;

    @Column(name = "footer_copyright", length = 200)
    private String footerCopyright;

    @Column(name = "footer_text", length = 500)
    private String footerText;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** The processes this client has configured. Deleting a client deletes these rows too. */
    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<ClientProcess> clientProcesses = new ArrayList<>();

    public void addClientProcess(ClientProcess cp) {
        cp.setClient(this);
        clientProcesses.add(cp);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public String getApplicationName() { return applicationName; }
    public void setApplicationName(String applicationName) { this.applicationName = applicationName; }
    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
    public ClientStatus getStatus() { return status; }
    public void setStatus(ClientStatus status) { this.status = status; }
    public boolean isFrozen() { return frozen; }
    public void setFrozen(boolean frozen) { this.frozen = frozen; }
    public Instant getFrozenAt() { return frozenAt; }
    public void setFrozenAt(Instant frozenAt) { this.frozenAt = frozenAt; }
    public String getFrozenBy() { return frozenBy; }
    public void setFrozenBy(String frozenBy) { this.frozenBy = frozenBy; }
    public String getHeaderColor() { return headerColor; }
    public void setHeaderColor(String headerColor) { this.headerColor = headerColor; }
    public String getFooterColor() { return footerColor; }
    public void setFooterColor(String footerColor) { this.footerColor = footerColor; }
    public String getFooterCompanyName() { return footerCompanyName; }
    public void setFooterCompanyName(String v) { this.footerCompanyName = v; }
    public String getFooterAddress() { return footerAddress; }
    public void setFooterAddress(String v) { this.footerAddress = v; }
    public String getFooterEmail() { return footerEmail; }
    public void setFooterEmail(String v) { this.footerEmail = v; }
    public String getFooterPhone() { return footerPhone; }
    public void setFooterPhone(String v) { this.footerPhone = v; }
    public String getFooterWebsite() { return footerWebsite; }
    public void setFooterWebsite(String v) { this.footerWebsite = v; }
    public String getFooterCopyright() { return footerCopyright; }
    public void setFooterCopyright(String v) { this.footerCopyright = v; }
    public String getFooterText() { return footerText; }
    public void setFooterText(String v) { this.footerText = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<ClientProcess> getClientProcesses() { return clientProcesses; }
}
