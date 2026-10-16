package com.example.recruitment.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Header/footer colours and footer details are checked on the server, not only in Angular. */
class ClientRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<ConstraintViolation<ClientRequest>> check(String header, String footer, FooterRequest f) {
        return validator.validate(new ClientRequest("Acme", null, "Acme Careers", "default", null, header, footer, f));
    }

    private static FooterRequest footer(String email, String phone, String site) {
        return new FooterRequest("Acme Ltd", "1 Main St", email, phone, site, "(c) Acme", "Equal opportunity employer");
    }

    @Test
    void validColoursAreAccepted() {
        for (String c : List.of("#1e40af", "#ABCDEF", "#fff", "#FfF", "")) {
            assertTrue(check(c, c, null).isEmpty(), c);
        }
    }

    @Test
    void leftOutColoursAreAccepted() {
        assertTrue(check(null, null, null).isEmpty());
    }

    @Test
    void invalidColoursAreRejected() {
        for (String c : List.of("red", "1e40af", "#12", "#12345", "#1234567", "#gggggg", "rgb(1,2,3)", "# 123456", " #123456")) {
            assertEquals(1, check(c, null, null).size(), c);
            assertEquals(1, check(null, c, null).size(), c);
        }
    }

    @Test
    void headerAndFooterColoursAreCheckedIndependently() {
        Set<ConstraintViolation<ClientRequest>> v = check("#123456", "nope", null);
        assertEquals(1, v.size());
        assertEquals("footerColor", v.iterator().next().getPropertyPath().toString());
    }

    @Test
    void aCompleteValidFooterIsAccepted() {
        assertTrue(check(null, null, footer("hr@acme.com", "+91 98765-43210", "https://www.acme.com/careers")).isEmpty());
    }

    @Test
    void everyFooterFieldIsOptional() {
        assertTrue(check(null, null, new FooterRequest(null, null, null, null, null, null, null)).isEmpty());
        assertTrue(check(null, null, new FooterRequest("", "", "", "", "", "", "")).isEmpty());
        assertTrue(check(null, null, new FooterRequest("  ", "  ", "  ", "  ", "  ", "  ", "  ")).isEmpty());
    }

    @Test
    void badContactEmailIsRejected() {
        for (String e : List.of("acme.com", "a@b", "a b@c.com", "@c.com")) {
            assertEquals(1, check(null, null, footer(e, null, null)).size(), e);
        }
    }

    @Test
    void badWebsiteIsRejected() {
        for (String w : List.of("acme.com", "www.acme.com", "ftp://acme.com", "https://", "javascript:alert(1)", "https://a cme.com")) {
            assertEquals(1, check(null, null, footer(null, null, w)).size(), w);
        }
    }

    @Test
    void badPhoneIsRejected() {
        for (String p : List.of("abc", "12", "98765<script>")) {
            assertEquals(1, check(null, null, footer(null, p, null)).size(), p);
        }
    }

    @Test
    void tooLongFooterFieldsAreRejected() {
        FooterRequest f = new FooterRequest("x".repeat(151), "x".repeat(301), null, null, null, "x".repeat(201), "x".repeat(501));
        assertEquals(4, check(null, null, f).size());
    }
}
