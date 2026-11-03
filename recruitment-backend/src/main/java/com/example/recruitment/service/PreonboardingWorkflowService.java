package com.example.recruitment.service;

import com.example.recruitment.dto.PreonboardingDocumentView;
import com.example.recruitment.dto.PreonboardingSubmissionView;
import com.example.recruitment.entity.*;
import com.example.recruitment.repository.*;
import com.example.recruitment.security.AuthUser;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Candidate document upload and HR review. Files are private and are never exposed as public URLs. */
@Service
public class PreonboardingWorkflowService {
 private static final long MAX_FILE=10L*1024*1024;
 private static final Map<String,String> ALLOWED=Map.of("application/pdf","pdf","image/jpeg","jpg","image/png","png");
 private final PreonboardingCaseRepository cases; private final PreonboardingAccessRepository accesses;
 private final PreonboardingDocumentRepository documents; private final PreonboardingSettingRepository settings;
 private final CandidateEventRepository events; private final JavaMailSender mail; private final Path root; private final String from;
 public PreonboardingWorkflowService(PreonboardingCaseRepository cases, PreonboardingAccessRepository accesses,
   PreonboardingDocumentRepository documents, PreonboardingSettingRepository settings, CandidateEventRepository events,
   JavaMailSender mail, @Value("${app.preonboarding-dir:private-preonboarding}") String uploadDir,
   @Value("${app.mail.from:${spring.mail.username:}}") String from) {
  this.cases=cases;this.accesses=accesses;this.documents=documents;this.settings=settings;this.events=events;this.mail=mail;
  this.root=Paths.get(uploadDir).toAbsolutePath().normalize().resolve("preonboarding");this.from=from;
  try{Files.createDirectories(root);}catch(IOException e){throw new IllegalStateException("Cannot create private pre-onboarding storage",e);}
 }
 @Transactional public PreonboardingDocumentView upload(String rawToken,String itemCode,MultipartFile file){
  PreonboardingAccess access=findToken(rawToken);if(!access.isVerified())throw new IllegalArgumentException("Verify your email before uploading documents");
  PreonboardingCase c=access.getPreonboardingCase();checkDeadline(c);
  PreonboardingItem item=PreonboardingItem.find(itemCode).orElseThrow(()->new IllegalArgumentException("Unknown document type"));
  if(item.getKind()!=PreonboardingItem.Kind.DOCUMENT)throw new IllegalArgumentException("This item is not a document");
  boolean enabled=settings.findByClientId(c.getClient().getId()).stream().anyMatch(s->s.getItemCode().equals(item.name())&&s.isEnabled());
  if(!enabled)throw new IllegalArgumentException("This document is not requested by HR");
  if(file==null||file.isEmpty())throw new IllegalArgumentException("Choose a file");if(file.getSize()>MAX_FILE)throw new IllegalArgumentException("Maximum file size is 10 MB");
  String ext=ALLOWED.get(file.getContentType());if(ext==null)throw new IllegalArgumentException("Only PDF, JPG and PNG files are accepted");
  try(var probe=file.getInputStream()){byte[] head=probe.readNBytes(8);boolean valid=switch(ext){case "pdf"->head.length>=5&&head[0]=='%'&&head[1]=='P'&&head[2]=='D'&&head[3]=='F'&&head[4]=='-';case "jpg"->head.length>=3&&(head[0]&255)==0xFF&&(head[1]&255)==0xD8&&(head[2]&255)==0xFF;case "png"->head.length>=8&&(head[0]&255)==0x89&&head[1]=='P'&&head[2]=='N'&&head[3]=='G';default->false;};if(!valid)throw new IllegalArgumentException("File content does not match its type");}catch(IOException e){throw new IllegalArgumentException("Could not inspect uploaded file");}
  String stored=UUID.randomUUID()+"."+ext;Path dir=root.resolve(String.valueOf(c.getId())).normalize();if(!dir.startsWith(root))throw new IllegalArgumentException("Invalid storage location");
  try{Files.createDirectories(dir);Path target=dir.resolve(stored);try(var input=file.getInputStream()){Files.copy(input,target,StandardCopyOption.REPLACE_EXISTING);}
   for(PreonboardingDocument previous:documents.findByPreonboardingCaseIdOrderByUploadedAtAsc(c.getId())){if(previous.getItemCode().equals(item.name()))previous.setReviewStatus("SUPERSEDED");}
   PreonboardingDocument d=new PreonboardingDocument();d.setPreonboardingCase(c);d.setItemCode(item.name());d.setOriginalName(safeName(file.getOriginalFilename()));d.setStoredName(stored);d.setContentType(file.getContentType());d.setFileSize(file.getSize());d.setReviewStatus("PENDING");
   c.setStatus(PreonboardingStatus.IN_PROGRESS);return view(documents.save(d));
  }catch(IOException e){throw new IllegalStateException("Could not save document",e);}
 }
 @Transactional(readOnly=true) public List<PreonboardingDocumentView> publicDocuments(String token){PreonboardingCase c=findToken(token).getPreonboardingCase();return documents.findByPreonboardingCaseIdOrderByUploadedAtAsc(c.getId()).stream().map(this::view).toList();}
 @Transactional(readOnly=true) public PreonboardingSubmissionView staffSubmission(Long candidateId,AuthUser user){PreonboardingCase c=staffCase(candidateId,user);return accesses.findByPreonboardingCaseId(c.getId()).map(a->new PreonboardingSubmissionView(c.getCandidate().getFullName(),a.getSubmittedDetails())).orElse(new PreonboardingSubmissionView(c.getCandidate().getFullName(),null));}
 @Transactional(readOnly=true) public List<PreonboardingDocumentView> staffDocuments(Long candidateId,AuthUser user){PreonboardingCase c=staffCase(candidateId,user);return documents.findByPreonboardingCaseIdOrderByUploadedAtAsc(c.getId()).stream().map(this::view).toList();}
 @Transactional public PreonboardingDocumentView review(Long candidateId,Long documentId,String action,String comment,AuthUser user){
  PreonboardingCase c=staffCase(candidateId,user);PreonboardingDocument d=documents.findByIdAndPreonboardingCaseId(documentId,c.getId()).orElseThrow(()->new IllegalArgumentException("Document not found"));
  if(!"APPROVED".equals(action)&&!"NEEDS_CORRECTION".equals(action))throw new IllegalArgumentException("Action must be APPROVED or NEEDS_CORRECTION");
  if("NEEDS_CORRECTION".equals(action)&&(comment==null||comment.isBlank()))throw new IllegalArgumentException("A correction comment is required");
  d.setReviewStatus(action);d.setReviewComment(comment==null?null:comment.trim());d.setReviewedAt(Instant.now());d.setReviewedBy(user.email());
  if("NEEDS_CORRECTION".equals(action)){c.setStatus(PreonboardingStatus.CORRECTION_REQUIRED);send(c.getCandidate().getEmail(),"Pre-onboarding document correction required","HR needs you to correct "+d.getItemCode()+": "+comment+"\nPlease use your original pre-onboarding link to upload the corrected document.");}
  event(c,"Pre-onboarding document "+action.toLowerCase().replace('_',' '),d.getItemCode()+ (comment==null?"":" — "+comment),user.email());return view(d);
 }
 @Transactional public void finalApprove(Long candidateId,String comment,AuthUser user){
  PreonboardingCase c=staffCase(candidateId,user);if(c.getStatus()!=PreonboardingStatus.SUBMITTED)throw new IllegalArgumentException("Candidate must submit pre-onboarding information before final approval");
  List<PreonboardingSetting> required=settings.findByClientId(c.getClient().getId()).stream().filter(s->s.isEnabled()&&s.isMandatory()&&PreonboardingItem.find(s.getItemCode()).map(i->i.getKind()==PreonboardingItem.Kind.DOCUMENT).orElse(false)).toList();
  List<PreonboardingDocument> docs=documents.findByPreonboardingCaseIdOrderByUploadedAtAsc(c.getId());
  for(PreonboardingSetting r:required)if(docs.stream().noneMatch(d->d.getItemCode().equals(r.getItemCode())&&"APPROVED".equals(d.getReviewStatus())))throw new IllegalArgumentException("Required document is not approved: "+r.getItemCode());
  if(docs.stream().anyMatch(d->"NEEDS_CORRECTION".equals(d.getReviewStatus())))throw new IllegalArgumentException("Resolve all document corrections before final approval");
  c.setStatus(PreonboardingStatus.COMPLETED);event(c,"Pre-onboarding completed",comment,user.email());send(c.getCandidate().getEmail(),"Pre-onboarding approved","Your pre-onboarding documents have been approved. HR will contact you about the next steps.");
 }
 @Transactional public void requestCorrection(Long candidateId,String comment,AuthUser user){if(comment==null||comment.isBlank())throw new IllegalArgumentException("Please enter correction instructions");PreonboardingCase c=staffCase(candidateId,user);c.setStatus(PreonboardingStatus.CORRECTION_REQUIRED);event(c,"Pre-onboarding correction requested",comment,user.email());send(c.getCandidate().getEmail(),"Pre-onboarding correction required",comment+"\nPlease use your original secure link to update your submission.");}
 @Transactional public void deleteFilesForClient(Long clientId){for(PreonboardingCase c:cases.findByClientId(clientId))deleteFilesForCase(c.getId());}
 @Transactional public void deleteFilesForCase(Long caseId){Path dir=root.resolve(String.valueOf(caseId)).normalize();if(!dir.startsWith(root)||!Files.exists(dir))return;try(var paths=Files.walk(dir)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});}catch(IOException ignored){}}
 @Transactional(readOnly=true) public Download download(Long candidateId,Long documentId,AuthUser user){PreonboardingCase c=staffCase(candidateId,user);PreonboardingDocument d=documents.findByIdAndPreonboardingCaseId(documentId,c.getId()).orElseThrow(()->new IllegalArgumentException("Document not found"));Path p=root.resolve(String.valueOf(c.getId())).resolve(d.getStoredName()).normalize();if(!p.startsWith(root)||!Files.isRegularFile(p))throw new IllegalArgumentException("Stored document is unavailable");return new Download(new FileSystemResource(p),d.getContentType(),d.getOriginalName());}
 private PreonboardingCase staffCase(Long candidateId,AuthUser u){requireStaff(u);PreonboardingCase c=cases.findByCandidateId(candidateId).orElseThrow(()->new IllegalArgumentException("Pre-onboarding case not found"));if(u.role()!=Role.ADMIN&&!Objects.equals(u.clientId(),c.getClient().getId()))throw new AccessDeniedException("Candidate belongs to another client");return c;}
 private PreonboardingAccess findToken(String token){if(token==null||token.length()<30||token.length()>200)throw new IllegalArgumentException("Invalid invitation link");String hash;try{hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}PreonboardingAccess a=accesses.findByTokenHash(hash).orElseThrow(()->new IllegalArgumentException("Invalid invitation link"));if(Instant.now().isAfter(a.getTokenExpiresAt()))throw new IllegalArgumentException("Invitation link expired");return a;}
 private void checkDeadline(PreonboardingCase c){if(c.getStatus()==PreonboardingStatus.COMPLETED)throw new IllegalArgumentException("Pre-onboarding is already completed");if(Instant.now().isAfter(c.getDueAt())){c.setStatus(PreonboardingStatus.EXPIRED);throw new IllegalArgumentException("Submission deadline has passed. Contact HR.");}}
 private void requireStaff(AuthUser u){if(u==null||(u.role()!=Role.ADMIN&&u.role()!=Role.HR_BP&&u.role()!=Role.RECRUITER))throw new AccessDeniedException("Only Admin, HR BP or Recruiter can review pre-onboarding");}
 private void event(PreonboardingCase c,String title,String details,String actor){CandidateEvent e=new CandidateEvent();e.setCandidate(c.getCandidate());e.setType(EventType.STAGE_DECISION);e.setTitle(title);e.setDetails(details);e.setActor(actor);events.save(e);}
 private PreonboardingDocumentView view(PreonboardingDocument d){return new PreonboardingDocumentView(d.getId(),d.getItemCode(),d.getOriginalName(),d.getContentType(),d.getFileSize(),d.getReviewStatus(),d.getReviewComment(),d.getUploadedAt(),d.getReviewedAt(),d.getReviewedBy());}
 private void send(String to,String subject,String body){try{MimeMessage m=mail.createMimeMessage();MimeMessageHelper h=new MimeMessageHelper(m,StandardCharsets.UTF_8.name());if(from!=null&&!from.isBlank())h.setFrom(from);h.setTo(to);h.setSubject(subject);h.setText(body);mail.send(m);}catch(Exception e){throw new IllegalStateException("Could not send pre-onboarding email",e);}}
 private String safeName(String n){String s=n==null?"document":Paths.get(n).getFileName().toString().replaceAll("[^A-Za-z0-9._-]","_");return s.length()>255?s.substring(s.length()-255):s;}
 public record Download(Resource resource,String contentType,String fileName){}
}
