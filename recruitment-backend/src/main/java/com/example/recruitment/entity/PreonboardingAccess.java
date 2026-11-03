package com.example.recruitment.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

/** Candidate's private, one-case invitation and OTP state. Only hashes are persisted. */
@Entity
@Table(name = "preonboarding_access", uniqueConstraints = @UniqueConstraint(columnNames = "case_id"))
public class PreonboardingAccess {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "case_id", nullable = false, unique = true)
    private PreonboardingCase preonboardingCase;
    @Column(name="token_hash", nullable=false, length=64) private String tokenHash;
    @Column(name="token_expires_at", nullable=false) private Instant tokenExpiresAt;
    @Column(name="otp_hash", length=64) private String otpHash;
    @Column(name="otp_expires_at") private Instant otpExpiresAt;
    @Column(name="otp_resend_after") private Instant otpResendAfter;
    @Column(name="otp_attempts", nullable=false) private int otpAttempts;
    @Column(name="reminder_sent_at") private Instant reminderSentAt;
    @Column(name="verified", nullable=false) private boolean verified;
    @Lob @Column(name="submitted_details") private String submittedDetails;
    @CreationTimestamp @Column(name="created_at", nullable=false, updatable=false) private Instant createdAt;
    public Long getId(){return id;} public PreonboardingCase getPreonboardingCase(){return preonboardingCase;} public void setPreonboardingCase(PreonboardingCase c){preonboardingCase=c;}
    public String getTokenHash(){return tokenHash;} public void setTokenHash(String v){tokenHash=v;} public Instant getTokenExpiresAt(){return tokenExpiresAt;} public void setTokenExpiresAt(Instant v){tokenExpiresAt=v;}
    public String getOtpHash(){return otpHash;} public void setOtpHash(String v){otpHash=v;} public Instant getOtpExpiresAt(){return otpExpiresAt;} public void setOtpExpiresAt(Instant v){otpExpiresAt=v;}
    public Instant getOtpResendAfter(){return otpResendAfter;} public void setOtpResendAfter(Instant v){otpResendAfter=v;} public int getOtpAttempts(){return otpAttempts;} public void setOtpAttempts(int v){otpAttempts=v;}
    public boolean isVerified(){return verified;} public void setVerified(boolean v){verified=v;} public Instant getReminderSentAt(){return reminderSentAt;} public void setReminderSentAt(Instant v){reminderSentAt=v;} public String getSubmittedDetails(){return submittedDetails;} public void setSubmittedDetails(String v){submittedDetails=v;}
}
