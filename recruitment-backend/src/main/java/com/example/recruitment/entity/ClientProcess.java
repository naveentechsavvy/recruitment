package com.example.recruitment.entity;

import jakarta.persistence.*;

/** Join table between a client and a process: "Samsung uses Technical Round, as step 4". */
@Entity
@Table(name = "client_processes",
       uniqueConstraints = @UniqueConstraint(columnNames = {"client_id", "process_id"}))
public class ClientProcess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "process_id", nullable = false)
    private RecruitmentProcess process;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public Long getId() { return id; }
    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }
    public RecruitmentProcess getProcess() { return process; }
    public void setProcess(RecruitmentProcess process) { this.process = process; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
}
