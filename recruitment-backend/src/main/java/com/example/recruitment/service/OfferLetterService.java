package com.example.recruitment.service;

import com.example.recruitment.entity.Candidate;
import com.example.recruitment.entity.Requirement;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds the offer letter PDF from a text template and emails it to the candidate.
 *
 * The template is the file offer-letter-template.txt. It can be replaced by your own text through
 * app.offer.template-path. In it, "# " starts a heading, a blank line starts a new paragraph, and these
 * placeholders are filled in: {{candidateName}} {{jobTitle}} {{jobId}} {{companyName}} {{ctc}} {{date}}
 * {{location}} {{department}}.
 */
@Service
public class OfferLetterService {
    private static final Logger log = LoggerFactory.getLogger(OfferLetterService.class);

    private static final String CLASSPATH_TEMPLATE = "offer-letter-template.txt";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String templatePath;
    private final String ctcUnit;

    public OfferLetterService(JavaMailSender mailSender,
                              @Value("${app.mail.from:${spring.mail.username:}}") String fromAddress,
                              @Value("${app.offer.template-path:}") String templatePath,
                              @Value("${app.offer.ctc-unit:LPA}") String ctcUnit) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.templatePath = templatePath;
        this.ctcUnit = ctcUnit == null ? "" : ctcUnit.trim();
    }

    /**
     * Generates the offer letter and emails it to the candidate, with the given people in CC.
     * The candidate's own address, blanks and duplicates are removed from the CC list.
     * If the email cannot be sent an IllegalArgumentException is thrown, so the caller can undo its change.
     *
     * @return the CC addresses the email was really sent to
     */
    public List<String> sendOffer(Candidate candidate, List<String> cc) {
        String to = candidate.getEmail() == null ? "" : candidate.getEmail().trim();
        if (to.isEmpty()) {
            throw new IllegalArgumentException("The candidate has no email address, so the offer letter cannot be sent");
        }

        Set<String> copies = new LinkedHashSet<>();
        if (cc != null) {
            for (String address : cc) {
                if (address == null || address.isBlank()) continue;
                String clean = address.trim();
                if (!clean.equalsIgnoreCase(to)) copies.add(clean);
            }
        }
        // The same address in different letter case counts once.
        Set<String> seen = new LinkedHashSet<>();
        copies.removeIf(a -> !seen.add(a.toLowerCase(Locale.ROOT)));

        byte[] pdf = generatePdf(candidate);
        String company = candidate.getClient() == null ? "our company" : candidate.getClient().getName();
        String job = jobTitle(candidate);
        String name = candidate.getFullName();

        String html = "<div style=\"font-family:Arial,Helvetica,sans-serif;font-size:15px;color:#35271f;line-height:1.5;\">"
                + "<p>Dear " + esc(name) + ",</p>"
                + "<p>Congratulations! We are delighted to offer you the position of <strong>" + esc(job)
                + "</strong> at <strong>" + esc(company) + "</strong>.</p>"
                + "<p>Your offer letter is attached to this email as a PDF. Please read it and reply to this email to confirm your acceptance.</p>"
                + "<p>Warm regards,<br>" + esc(company) + "<br>Human Resources</p></div>";
        String plain = "Dear " + name + ",\n\n"
                + "Congratulations! We are delighted to offer you the position of " + job + " at " + company + ".\n\n"
                + "Your offer letter is attached to this email as a PDF. Please read it and reply to this email to confirm your acceptance.\n\n"
                + "Warm regards,\n" + company + "\nHuman Resources";

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            if (fromAddress != null && !fromAddress.isBlank()) helper.setFrom(fromAddress, company);
            helper.setTo(to);
            if (!copies.isEmpty()) helper.setCc(copies.toArray(new String[0]));
            helper.setSubject("Offer letter: " + job + " at " + company);
            helper.setText(plain, html);
            helper.addAttachment(fileName(name), new ByteArrayResource(pdf), "application/pdf");
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException | MailException e) {
            log.error("Could not send the offer letter to {}", to, e);
            throw new IllegalArgumentException("The offer letter email could not be sent. Please try again or contact your administrator");
        }

        return List.copyOf(copies);
    }

    /** The offer letter as a PDF, built from the template. */
    public byte[] generatePdf(Candidate candidate) {
        Map<String, String> values = values(candidate);
        String template = loadTemplate();

        Font heading = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 11);

        Document document = new Document(PageSize.A4, 56, 56, 64, 64);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            for (String block : template.replace("\r\n", "\n").split("\n\\s*\n")) {
                String text = fill(block.strip(), values);
                if (text.isEmpty()) continue;

                Paragraph paragraph;
                if (text.startsWith("# ")) {
                    paragraph = new Paragraph(text.substring(2).strip(), heading);
                    paragraph.setAlignment(Element.ALIGN_CENTER);
                    paragraph.setSpacingAfter(18);
                } else {
                    paragraph = new Paragraph(text, body);
                    paragraph.setLeading(16);
                    paragraph.setSpacingAfter(10);
                }
                document.add(paragraph);
            }
        } catch (DocumentException e) {
            throw new IllegalStateException("The offer letter could not be created", e);
        } finally {
            if (document.isOpen()) document.close();
        }
        return out.toByteArray();
    }

    private Map<String, String> values(Candidate c) {
        Requirement r = c.getRequirement();
        Map<String, String> v = new LinkedHashMap<>();
        v.put("candidateName", nullToEmpty(c.getFullName()));
        v.put("jobTitle", jobTitle(c));
        v.put("jobId", r == null ? "" : nullToEmpty(r.getJobId()));
        v.put("companyName", c.getClient() == null ? "" : nullToEmpty(c.getClient().getName()));
        v.put("ctc", ctc(c.getExpectedCtc()));
        v.put("date", LocalDate.now().format(DATE));
        v.put("location", r == null ? "" : nullToEmpty(r.getLocation()));
        v.put("department", r == null ? "" : nullToEmpty(r.getDepartment()));
        return v;
    }

    private String ctc(BigDecimal amount) {
        if (amount == null) return "as discussed with you";
        String number = amount.stripTrailingZeros().toPlainString();
        return ctcUnit.isEmpty() ? number : number + " " + ctcUnit;
    }

    private static String jobTitle(Candidate c) {
        Requirement r = c.getRequirement();
        return r == null || r.getJobTitle() == null ? "the advertised position" : r.getJobTitle();
    }

    private static String fill(String text, Map<String, String> values) {
        String result = text;
        for (Map.Entry<String, String> e : values.entrySet()) {
            result = result.replace("{{" + e.getKey() + "}}", e.getValue());
        }
        return result;
    }

    private String loadTemplate() {
        try {
            if (templatePath != null && !templatePath.isBlank()) {
                return Files.readString(Path.of(templatePath.trim()), StandardCharsets.UTF_8);
            }
            try (InputStream in = OfferLetterService.class.getClassLoader().getResourceAsStream(CLASSPATH_TEMPLATE)) {
                if (in == null) throw new IllegalStateException("The offer letter template is missing");
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalStateException("The offer letter template could not be read", e);
        }
    }

    private static String fileName(String candidateName) {
        String safe = candidateName == null ? "" : candidateName.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return "Offer-Letter" + (safe.isEmpty() ? "" : "-" + safe) + ".pdf";
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
