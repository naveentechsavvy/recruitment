package com.example.recruitment.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * One uploaded resume file, stored inside the database (table resume_files).
 * It is kept in its own table, away from the candidates table, so loading a candidate list never loads file data.
 * The candidate points to it by {@link #filename} (candidates.resume_file), the same random name that used to be a file name on disk.
 */
@Entity
@Table(name = "resume_files",
        uniqueConstraints = @UniqueConstraint(name = "uk_resume_file_filename", columnNames = "filename"))
public class ResumeFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Random name such as 3f6508b2-....pdf. Not the name the candidate uploaded. */
    @Column(nullable = false, length = 100)
    private String filename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGBLOB")
    private byte[] data;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ResumeFile() { }

    public ResumeFile(String filename, String contentType, byte[] data) {
        this.filename = filename;
        this.contentType = contentType;
        this.data = data;
        this.sizeBytes = data.length;
    }

    public Long getId() { return id; }
    public String getFilename() { return filename; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public byte[] getData() { return data; }
    public Instant getCreatedAt() { return createdAt; }
}
