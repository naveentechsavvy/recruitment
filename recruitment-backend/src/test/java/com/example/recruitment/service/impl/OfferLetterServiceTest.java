package com.example.recruitment.service;

import com.example.recruitment.entity.Candidate;
import com.example.recruitment.entity.Client;
import com.example.recruitment.entity.Requirement;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The offer letter PDF and the email that carries it. The mail sender is mocked, so nothing is really sent. */
class OfferLetterServiceTest {

    JavaMailSender sender;
    OfferLetterService service;
    Candidate candidate;

    @BeforeEach
    void setUp() {
        sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
        service = new OfferLetterService(sender, "hr@example.com", "", "LPA");

        Client client = new Client();
        client.setName("VISY");
        Requirement requirement = new Requirement();
        requirement.setJobTitle("Full stack developer");

        candidate = new Candidate();
        candidate.setFullName("Hybris");
        candidate.setEmail("Cand@Example.com");
        candidate.setClient(client);
        candidate.setRequirement(requirement);
        candidate.setExpectedCtc(new BigDecimal("15.00"));
    }

    @Test
    void theLetterIsAPdfWithTheCandidateJobAndCtcFilledIn() throws Exception {
        byte[] pdf = service.generatePdf(candidate);

        assertEquals("%PDF-", new String(pdf, 0, 5));
        PdfReader reader = new PdfReader(pdf);
        assertEquals(1, reader.getNumberOfPages());
        String text = new PdfTextExtractor(reader).getTextFromPage(1);
        assertTrue(text.contains("Dear Hybris"));
        assertTrue(text.contains("Full stack developer"));
        assertTrue(text.contains("VISY"));
        assertTrue(text.contains("15 LPA"));
        assertFalse(text.contains("{{"), "every placeholder must be filled in");
    }

    @Test
    void withoutACtcTheLetterSaysItWasDiscussed() throws Exception {
        candidate.setExpectedCtc(null);

        PdfReader reader = new PdfReader(service.generatePdf(candidate));
        assertTrue(new PdfTextExtractor(reader).getTextFromPage(1).contains("as discussed with you"));
    }

    @Test
    void theEmailGoesToTheCandidateWithTheOthersInCc() throws Exception {
        List<String> sentCc = service.sendOffer(candidate,
                Arrays.asList("hm@example.com", "HRBP@example.com", "hrbp@EXAMPLE.com", "cand@example.com", " ", null));

        // the candidate's own address, blanks and repeats are dropped
        assertEquals(List.of("hm@example.com", "HRBP@example.com"), sentCc);

        ArgumentCaptor<MimeMessage> message = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(message.capture());
        MimeMessage sent = message.getValue();

        assertEquals("Cand@Example.com", ((InternetAddress) sent.getRecipients(Message.RecipientType.TO)[0]).getAddress());
        Message.RecipientType cc = Message.RecipientType.CC;
        assertEquals(2, sent.getRecipients(cc).length);
        assertEquals("Offer letter: Full stack developer at VISY", sent.getSubject());
    }

    @Test
    void aCandidateWithoutAnEmailAddressGetsNothing() {
        candidate.setEmail("  ");

        assertThrows(IllegalArgumentException.class, () -> service.sendOffer(candidate, List.of("hm@example.com")));
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void ifTheMailServerFailsTheCallerIsTold() {
        doThrow(new MailSendException("SMTP down")).when(sender).send(any(MimeMessage.class));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.sendOffer(candidate, List.of("hm@example.com")));
        assertTrue(e.getMessage().contains("could not be sent"));
    }
}
