package com.example.recruitment.service;

import com.example.recruitment.entity.PreonboardingAccess;
import com.example.recruitment.entity.PreonboardingCase;
import com.example.recruitment.entity.PreonboardingStatus;
import com.example.recruitment.repository.PreonboardingAccessRepository;
import com.example.recruitment.repository.PreonboardingCaseRepository;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Sends one reminder 24 hours before the candidate's deadline and marks overdue cases expired. */
@Component
public class PreonboardingReminderJob {
 private final PreonboardingCaseRepository cases;
 private final PreonboardingAccessRepository access;
 private final JavaMailSender mail;
 private final String from;
 public PreonboardingReminderJob(PreonboardingCaseRepository cases,PreonboardingAccessRepository access,JavaMailSender mail,
   @Value("${app.mail.from:${spring.mail.username:}}") String from){this.cases=cases;this.access=access;this.mail=mail;this.from=from;}
 @Scheduled(cron="${app.preonboarding.reminder-cron:0 0 9 * * *}")
 @Transactional
 public void sendReminders(){
  if(from==null||from.isBlank())return; // mail is not configured in this environment
  Instant now=Instant.now();Instant in24=now.plus(24,ChronoUnit.HOURS);
  for(PreonboardingCase c:cases.findAll()){
   if(c.getStatus()==PreonboardingStatus.COMPLETED||c.getStatus()==PreonboardingStatus.EXPIRED)continue;
   if(!c.getDueAt().isAfter(now)){c.setStatus(PreonboardingStatus.EXPIRED);continue;}
   if(c.getDueAt().isAfter(in24))continue;
   PreonboardingAccess a=access.findByPreonboardingCaseId(c.getId()).orElse(null);
   if(a==null||a.getReminderSentAt()!=null)continue;
   try{MimeMessage m=mail.createMimeMessage();MimeMessageHelper h=new MimeMessageHelper(m,StandardCharsets.UTF_8.name());if(from!=null&&!from.isBlank())h.setFrom(from);h.setTo(c.getCandidate().getEmail());h.setSubject("Reminder: complete your pre-onboarding");h.setText("Hello "+c.getCandidate().getFullName()+",\n\nThis is a reminder to complete your pre-onboarding before "+c.getDueAt()+". Please use the secure link in your invitation email. If you need a fresh link, contact HR.");mail.send(m);a.setReminderSentAt(now);}
   catch(Exception e){throw new IllegalStateException("Could not send pre-onboarding reminder",e);}
  }
 }
}
