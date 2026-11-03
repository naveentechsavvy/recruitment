package com.example.recruitment.service;

import com.example.recruitment.dto.PreonboardingPublicView;
import com.example.recruitment.entity.*;
import com.example.recruitment.repository.*;
import com.example.recruitment.security.AuthUser;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/** Public candidate access. Invitation and OTP secrets are random and only their SHA-256 hashes are stored. */
@Service
public class PreonboardingAccessService {
 private static final SecureRandom RANDOM=new SecureRandom();
 private final PreonboardingCaseRepository cases; private final PreonboardingAccessRepository access;
 private final CandidateRepository candidates; private final PreonboardingSettingRepository settings; private final PreonboardingDocumentRepository documents; private final CandidateEventRepository events; private final JavaMailSender mail;
 private final String frontendUrl, from;
 public PreonboardingAccessService(PreonboardingCaseRepository cases, PreonboardingAccessRepository access,
   CandidateRepository candidates, PreonboardingSettingRepository settings, PreonboardingDocumentRepository documents, CandidateEventRepository events, JavaMailSender mail,
   @Value("${app.frontend-url:http://localhost:4200}") String frontendUrl,
   @Value("${app.mail.from:${spring.mail.username:}}") String from){
  this.cases=cases;this.access=access;this.candidates=candidates;this.settings=settings;this.documents=documents;this.events=events;this.mail=mail;
  this.frontendUrl=frontendUrl.replaceAll("/+$","");this.from=from;
 }
 @Transactional public void inviteSystem(Candidate candidate){
  PreonboardingCase c=cases.findByCandidateId(candidate.getId()).orElseThrow(()->new IllegalArgumentException("Pre-onboarding case not found"));
  issueInvitation(candidate,c);
 }
 @Transactional public String invite(Long candidateId, AuthUser user){
  requireStaff(user); Candidate candidate=candidates.findById(candidateId).orElseThrow(()->new IllegalArgumentException("Candidate not found"));
  if(user.role()!=Role.ADMIN && !candidate.getClient().getId().equals(user.clientId())) throw new AccessDeniedException("Candidate belongs to another client");
  PreonboardingCase c=cases.findByCandidateId(candidateId).orElseThrow(()->new IllegalArgumentException("Pre-onboarding case not found. Candidate must accept the offer first."));
  issueInvitation(candidate,c);
  return "Pre-onboarding invitation sent to "+mask(candidate.getEmail());
 }
 private void issueInvitation(Candidate candidate,PreonboardingCase c){
  Instant now=Instant.now();if(!c.getDueAt().isAfter(now))throw new IllegalArgumentException("The submission deadline has passed. HR must extend the deadline before sending a new invitation.");
  String raw=token(); PreonboardingAccess a=access.findByPreonboardingCaseId(c.getId()).orElseGet(PreonboardingAccess::new);
  a.setPreonboardingCase(c);a.setTokenHash(hash(raw));a.setTokenExpiresAt(c.getDueAt());a.setVerified(false);a.setOtpHash(null);a.setOtpExpiresAt(null);a.setOtpResendAfter(null);a.setOtpAttempts(0);a.setReminderSentAt(null);access.save(a);c.setStatus(PreonboardingStatus.INVITED);
  send(candidate.getEmail(),"Complete your pre-onboarding", "Hello "+candidate.getFullName()+",\n\nPlease complete your pre-onboarding using this secure link:\n"+frontendUrl+"/preonboarding/"+raw+"\n\nThis link expires on "+a.getTokenExpiresAt()+".");
  event(c,"Pre-onboarding invitation sent","Secure invitation sent to candidate email.","System");
 }
 @Transactional public PreonboardingPublicView requestOtp(String raw){
  PreonboardingAccess a=find(raw); Instant now=Instant.now();if(a.getPreonboardingCase().getStatus()==PreonboardingStatus.COMPLETED)throw new IllegalArgumentException("Pre-onboarding is already completed");
  if(a.getOtpResendAfter()!=null && now.isBefore(a.getOtpResendAfter())) throw new IllegalArgumentException("Please wait before requesting another OTP");
  String otp=String.format(Locale.ROOT,"%06d",RANDOM.nextInt(1_000_000));a.setOtpHash(hash(otp));a.setOtpExpiresAt(now.plus(5,ChronoUnit.MINUTES));a.setOtpResendAfter(now.plusSeconds(45));a.setOtpAttempts(0);a.setVerified(false);
  Candidate c=a.getPreonboardingCase().getCandidate();send(c.getEmail(),"Your pre-onboarding verification code","Your verification code is "+otp+". It expires in 5 minutes. Do not share this code.");
  return view(a);
 }
 @Transactional(noRollbackFor = IllegalArgumentException.class) public PreonboardingPublicView verify(String raw,String otp){
  PreonboardingAccess a=find(raw); if(a.getOtpHash()==null || a.getOtpExpiresAt()==null || Instant.now().isAfter(a.getOtpExpiresAt())) throw new IllegalArgumentException("OTP expired. Request a new code.");
  if(a.getOtpAttempts()>=5) throw new IllegalArgumentException("Too many incorrect attempts. Request a new code.");
  a.setOtpAttempts(a.getOtpAttempts()+1);
  if(otp==null || !MessageDigest.isEqual(a.getOtpHash().getBytes(StandardCharsets.UTF_8),hash(otp.trim()).getBytes(StandardCharsets.UTF_8))) throw new IllegalArgumentException("Incorrect verification code");
  a.setVerified(true);a.setOtpHash(null);a.setOtpExpiresAt(null);event(a.getPreonboardingCase(),"Candidate email verified","Candidate verified their email using OTP.",a.getPreonboardingCase().getCandidate().getEmail());return view(a);
 }
 @Transactional public PreonboardingPublicView submit(String raw,String details){
  PreonboardingAccess a=find(raw);if(!a.isVerified())throw new IllegalArgumentException("Verify your email before submitting");
  if(details==null || details.isBlank() || details.length()>20000)throw new IllegalArgumentException("Please enter your details (maximum 20,000 characters)");
  PreonboardingCase c=a.getPreonboardingCase();if(c.getStatus()==PreonboardingStatus.COMPLETED)throw new IllegalArgumentException("Pre-onboarding is already completed");if(Instant.now().isAfter(c.getDueAt())){c.setStatus(PreonboardingStatus.EXPIRED);throw new IllegalArgumentException("The submission deadline has passed. Contact HR.");}
  
  for(PreonboardingSetting setting:settings.findByClientId(c.getClient().getId())){
   if(!setting.isEnabled()||!setting.isMandatory()) continue;
   var item=PreonboardingItem.find(setting.getItemCode()).orElse(null);
   if(item==null) continue;
   if(item.getKind()==PreonboardingItem.Kind.DETAIL){
    try{var parsed=new com.fasterxml.jackson.databind.ObjectMapper().readTree(details);if(!parsed.hasNonNull(item.name())||parsed.get(item.name()).asText().isBlank())throw new IllegalArgumentException("Please complete: "+item.getLabel());}
    catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("Please submit personal details in the form");}
   } else if(documents.findByPreonboardingCaseIdOrderByUploadedAtAsc(c.getId()).stream().noneMatch(d->d.getItemCode().equals(item.name())&& !"SUPERSEDED".equals(d.getReviewStatus()))) {
    throw new IllegalArgumentException("Please upload the required document: "+item.getLabel());
   }
  }
  a.setSubmittedDetails(details);c.setStatus(PreonboardingStatus.SUBMITTED);event(c,"Candidate submitted pre-onboarding information","Candidate submitted personal details and required documents.",c.getCandidate().getEmail());send(c.getCandidate().getEmail(),"Pre-onboarding submission received","Hello "+c.getCandidate().getFullName()+",\n\nWe received your pre-onboarding details and documents. HR will review them and contact you if anything needs correction.");return view(a);
 }
 @Transactional(readOnly=true) public PreonboardingPublicView describe(String raw){return view(find(raw));}
 private PreonboardingAccess find(String raw){if(raw==null||raw.length()<30||raw.length()>200)throw new IllegalArgumentException("Invalid invitation link");PreonboardingAccess a=access.findByTokenHash(hash(raw)).orElseThrow(()->new IllegalArgumentException("Invalid invitation link"));if(Instant.now().isAfter(a.getTokenExpiresAt()))throw new IllegalArgumentException("Invitation link expired. Contact HR for a new link.");return a;}
 private PreonboardingPublicView view(PreonboardingAccess a){
  PreonboardingCase c=a.getPreonboardingCase();
  var items=settings.findByClientId(c.getClient().getId()).stream().filter(PreonboardingSetting::isEnabled)
    .map(s->PreonboardingItem.find(s.getItemCode()).map(i->new com.example.recruitment.dto.PreonboardingPublicItemView(i.name(),i.getKind().name(),i.getLabel(),s.isMandatory())).orElse(null))
    .filter(java.util.Objects::nonNull).toList();
  return new PreonboardingPublicView(c.getCandidate().getFullName(),c.getClient().getName(),c.getStatus().name(),c.getDueAt().toString(),a.isVerified(),items);
 }
 private void event(PreonboardingCase c,String title,String details,String actor){CandidateEvent e=new CandidateEvent();e.setCandidate(c.getCandidate());e.setType(EventType.STAGE_DECISION);e.setTitle(title);e.setDetails(details);e.setActor(actor);events.save(e);}
 private void requireStaff(AuthUser u){if(u==null||(u.role()!=Role.ADMIN&&u.role()!=Role.HR_BP&&u.role()!=Role.RECRUITER))throw new AccessDeniedException("Only HR BP, Recruiter or Admin can send invitations");}
 private void send(String to,String subject,String body){try{MimeMessage m=mail.createMimeMessage();MimeMessageHelper h=new MimeMessageHelper(m, StandardCharsets.UTF_8.name());if(from!=null&&!from.isBlank())h.setFrom(from);h.setTo(to);h.setSubject(subject);h.setText(body);mail.send(m);}catch(Exception e){throw new IllegalStateException("Could not send pre-onboarding email",e);}}
 private static String token(){byte[] b=new byte[32];RANDOM.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
 private static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private static String mask(String email){int at=email.indexOf('@');return at<2?"***":email.substring(0,2)+"***"+email.substring(at);}
}
