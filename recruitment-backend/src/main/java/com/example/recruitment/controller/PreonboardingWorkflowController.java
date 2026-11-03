package com.example.recruitment.controller;

import com.example.recruitment.dto.PreonboardingDocumentView;
import com.example.recruitment.dto.PreonboardingReviewRequest;
import com.example.recruitment.dto.PreonboardingSubmissionView;
import com.example.recruitment.security.AuthUser;
import com.example.recruitment.service.PreonboardingWorkflowService;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
public class PreonboardingWorkflowController {
 private final PreonboardingWorkflowService service;
 public PreonboardingWorkflowController(PreonboardingWorkflowService service){this.service=service;}
 @PostMapping(value="/api/public/preonboarding/{token}/documents",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
 public PreonboardingDocumentView upload(@PathVariable String token,@RequestParam String itemCode,@RequestParam("file") MultipartFile file){return service.upload(token,itemCode,file);}
 @GetMapping("/api/public/preonboarding/{token}/documents")
 public List<PreonboardingDocumentView> publicDocuments(@PathVariable String token){return service.publicDocuments(token);}
 @GetMapping("/api/site/candidates/{candidateId}/preonboarding/submission")
 public PreonboardingSubmissionView submission(@PathVariable Long candidateId,@AuthenticationPrincipal AuthUser user){return service.staffSubmission(candidateId,user);}
 @GetMapping("/api/site/candidates/{candidateId}/preonboarding/documents")
 public List<PreonboardingDocumentView> documents(@PathVariable Long candidateId,@AuthenticationPrincipal AuthUser user){return service.staffDocuments(candidateId,user);}
 @PostMapping("/api/site/candidates/{candidateId}/preonboarding/documents/{documentId}/review")
 public PreonboardingDocumentView review(@PathVariable Long candidateId,@PathVariable Long documentId,@RequestBody PreonboardingReviewRequest request,@AuthenticationPrincipal AuthUser user){return service.review(candidateId,documentId,request.action(),request.comment(),user);}
 @GetMapping("/api/site/candidates/{candidateId}/preonboarding/documents/{documentId}/download")
 public ResponseEntity<Resource> download(@PathVariable Long candidateId,@PathVariable Long documentId,@AuthenticationPrincipal AuthUser user){
  var d=service.download(candidateId,documentId,user);return ResponseEntity.ok().contentType(MediaType.parseMediaType(d.contentType())).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(d.fileName(),StandardCharsets.UTF_8).build().toString()).header("X-Content-Type-Options","nosniff").body(d.resource());
 }
 @PostMapping("/api/site/candidates/{candidateId}/preonboarding/correction")
 public ResponseEntity<Void> correction(@PathVariable Long candidateId,@RequestBody PreonboardingReviewRequest request,@AuthenticationPrincipal AuthUser user){service.requestCorrection(candidateId,request.comment(),user);return ResponseEntity.noContent().build();}
 @PostMapping("/api/site/candidates/{candidateId}/preonboarding/approve")
 public ResponseEntity<Void> approve(@PathVariable Long candidateId,@RequestBody(required=false) PreonboardingReviewRequest request,@AuthenticationPrincipal AuthUser user){service.finalApprove(candidateId,request==null?null:request.comment(),user);return ResponseEntity.noContent().build();}
}
