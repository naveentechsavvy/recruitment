package com.example.recruitment.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name="preonboarding_documents", indexes=@Index(name="idx_preonb_doc_case", columnList="case_id"))
public class PreonboardingDocument {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="case_id", nullable=false) private PreonboardingCase preonboardingCase;
 @Column(name="item_code", nullable=false, length=60) private String itemCode;
 @Column(name="original_name", nullable=false, length=255) private String originalName;
 @Column(name="stored_name", nullable=false, length=100) private String storedName;
 @Column(name="content_type", nullable=false, length=100) private String contentType;
 @Column(name="file_size", nullable=false) private long fileSize;
 @Column(name="review_status", nullable=false, length=30) private String reviewStatus="PENDING";
 @Column(name="review_comment", length=1000) private String reviewComment;
 @CreationTimestamp @Column(name="uploaded_at", nullable=false, updatable=false) private Instant uploadedAt;
 @Column(name="reviewed_at") private Instant reviewedAt;
 @Column(name="reviewed_by", length=150) private String reviewedBy;
 public Long getId(){return id;} public PreonboardingCase getPreonboardingCase(){return preonboardingCase;} public void setPreonboardingCase(PreonboardingCase c){preonboardingCase=c;}
 public String getItemCode(){return itemCode;} public void setItemCode(String v){itemCode=v;} public String getOriginalName(){return originalName;} public void setOriginalName(String v){originalName=v;}
 public String getStoredName(){return storedName;} public void setStoredName(String v){storedName=v;} public String getContentType(){return contentType;} public void setContentType(String v){contentType=v;}
 public long getFileSize(){return fileSize;} public void setFileSize(long v){fileSize=v;} public String getReviewStatus(){return reviewStatus;} public void setReviewStatus(String v){reviewStatus=v;}
 public String getReviewComment(){return reviewComment;} public void setReviewComment(String v){reviewComment=v;} public Instant getUploadedAt(){return uploadedAt;} public Instant getReviewedAt(){return reviewedAt;}
 public void setReviewedAt(Instant v){reviewedAt=v;} public String getReviewedBy(){return reviewedBy;} public void setReviewedBy(String v){reviewedBy=v;}
}
