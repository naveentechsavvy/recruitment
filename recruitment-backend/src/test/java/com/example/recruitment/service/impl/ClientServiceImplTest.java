package com.example.recruitment.service.impl;

import com.example.recruitment.dto.ClientRequest;
import com.example.recruitment.dto.ClientResponse;
import com.example.recruitment.dto.FooterRequest;
import com.example.recruitment.dto.PublicClientResponse;
import com.example.recruitment.entity.Client;
import com.example.recruitment.entity.ClientStatus;
import com.example.recruitment.exception.ResourceNotFoundException;
import com.example.recruitment.repository.ClientProcessRepository;
import com.example.recruitment.repository.ClientRepository;
import com.example.recruitment.repository.RecruitmentProcessRepository;
import com.example.recruitment.service.ClientSlugGenerator;
import com.example.recruitment.service.FileStorageService;
import com.example.recruitment.service.ResumeStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientServiceImplTest {

    @Mock ClientRepository clients;
    @Mock ClientProcessRepository clientProcesses;
    @Mock RecruitmentProcessRepository processes;
    @Mock FileStorageService fileStorage;
    @Mock ClientSlugGenerator slugs;
    @Mock ResumeStorageService resumeStorage;

    ClientServiceImpl service;
    Client existing;

    @BeforeEach
    void setUp() {
        service = new ClientServiceImpl(clients, clientProcesses, processes, fileStorage, slugs, resumeStorage);
        when(slugs.uniqueSlug(any())).thenReturn("acme");
        when(clients.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        existing = new Client();
        ReflectionTestUtils.setField(existing, "id", 4L);
        existing.setName("Acme");
        existing.setSlug("acme");
        existing.setStatus(ClientStatus.ACTIVE);
        when(clients.findById(4L)).thenReturn(Optional.of(existing));
        when(clients.findById(99L)).thenReturn(Optional.empty());
    }

    private static ClientRequest request(String name, ClientStatus status, String header, String footerColor, FooterRequest footer) {
        return new ClientRequest(name, null, "Acme Careers", "default", status, header, footerColor, footer);
    }

    // ---------------------------------------------------------------- freeze / unfreeze

    @Test
    void freezeMarksThePortalFrozenAndKeepsItsData() {
        ClientResponse r = service.freezeClient(4L, "admin@example.com");

        assertTrue(r.frozen());
        assertEquals("admin@example.com", r.frozenBy());
        assertNotNull(r.frozenAt());
        assertTrue(existing.isFrozen());
        // nothing else changes
        assertEquals("Acme", r.name());
        assertEquals("acme", r.slug());
        assertEquals(ClientStatus.ACTIVE, r.status());
        verify(clients, never()).delete(any());
    }

    @Test
    void freezingAFrozenPortalIsRefused() {
        service.freezeClient(4L, "admin@example.com");
        assertThrows(IllegalArgumentException.class, () -> service.freezeClient(4L, "admin@example.com"));
    }

    @Test
    void unfreezeRestoresThePortal() {
        service.freezeClient(4L, "admin@example.com");

        ClientResponse r = service.unfreezeClient(4L);

        assertFalse(r.frozen());
        assertNull(r.frozenAt());
        assertNull(r.frozenBy());
        assertFalse(existing.isFrozen());
    }

    @Test
    void unfreezingAPortalThatIsNotFrozenIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> service.unfreezeClient(4L));
    }

    @Test
    void freezeAndUnfreezeOnAMissingPortalGiveNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.freezeClient(99L, "a@example.com"));
        assertThrows(ResourceNotFoundException.class, () -> service.unfreezeClient(99L));
    }

    @Test
    void freezingOnePortalDoesNotTouchAnother() {
        Client other = new Client();
        ReflectionTestUtils.setField(other, "id", 5L);
        when(clients.findById(5L)).thenReturn(Optional.of(other));

        service.freezeClient(4L, "admin@example.com");

        assertFalse(other.isFrozen());
    }

    @Test
    void aFrozenPortalCanStillBeEdited() {
        service.freezeClient(4L, "admin@example.com");

        ClientResponse r = service.updateClient(4L, request("Acme Corp", null, null, null, null));

        assertEquals("Acme Corp", r.name());
        assertTrue(r.frozen());
    }

    @Test
    void thePublicPageKnowsThePortalIsFrozen() {
        service.freezeClient(4L, "admin@example.com");
        assertTrue(PublicClientResponse.from(existing).frozen());
    }

    // ---------------------------------------------------------------- application: Save, not a second Generate

    @Test
    void aNewDraftApplicationIsNotGeneratedYet() {
        ClientResponse r = service.createClient(request("New Co", null, null, null, null));

        assertFalse(r.applicationGenerated());
        assertEquals(ClientStatus.DRAFT, r.status());
        assertEquals("acme", r.slug());
    }

    @Test
    void anApplicationThatIsReadyOrActiveIsAlreadyGenerated() {
        existing.setStatus(ClientStatus.READY);
        assertTrue(ClientResponse.from(existing).applicationGenerated());
        existing.setStatus(ClientStatus.ACTIVE);
        assertTrue(ClientResponse.from(existing).applicationGenerated());
    }

    @Test
    void updatingChangesTheSameRecordAndNeverCreatesAnother() {
        ClientResponse r = service.updateClient(4L, request("Acme Renamed", null, null, null, null));

        assertEquals(4L, r.id());
        assertEquals("acme", r.slug()); // the public link does not change when the name does
        assertEquals(ClientStatus.ACTIVE, r.status()); // status is kept when the request leaves it out
        verify(clients, never()).save(any());
    }

    @Test
    void updatingAMissingApplicationGivesNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.updateClient(99L, request("X", null, null, null, null)));
    }

    // ---------------------------------------------------------------- header and footer colours

    @Test
    void coloursAreSavedAndNormalised() {
        ClientResponse r = service.updateClient(4L, request("Acme", null, "#ABC", "  #1E40AF ", null));

        assertEquals("#aabbcc", r.headerColor());
        assertEquals("#1e40af", r.footerColor());
    }

    @Test
    void headerAndFooterColoursAreIndependent() {
        service.updateClient(4L, request("Acme", null, "#111111", "#222222", null));

        ClientResponse r = service.updateClient(4L, request("Acme", null, "#333333", null, null)); // footer left out

        assertEquals("#333333", r.headerColor());
        assertEquals("#222222", r.footerColor()); // kept
    }

    @Test
    void anEmptyColourClearsItAndLeftOutKeepsIt() {
        service.updateClient(4L, request("Acme", null, "#111111", "#222222", null));

        ClientResponse cleared = service.updateClient(4L, request("Acme", null, "", null, null));

        assertNull(cleared.headerColor());
        assertEquals("#222222", cleared.footerColor());
    }

    @Test
    void normalizeColorHandlesTheSupportedForms() {
        assertEquals("#aabbcc", ClientServiceImpl.normalizeColor("#AABBCC"));
        assertEquals("#aabbcc", ClientServiceImpl.normalizeColor("#abc"));
        assertNull(ClientServiceImpl.normalizeColor(""));
        assertNull(ClientServiceImpl.normalizeColor("   "));
        assertNull(ClientServiceImpl.normalizeColor(null));
    }

    // ---------------------------------------------------------------- footer details

    private static FooterRequest fullFooter() {
        return new FooterRequest("  Acme Ltd ", "1 Main St, Erode", "hr@acme.com", "+91 98765 43210",
                "https://acme.com", "(c) 2026 Acme", "We are an equal opportunity employer");
    }

    @Test
    void footerDetailsAreSavedTrimmedWhenTheClientIsCreated() {
        ClientResponse r = service.createClient(request("Acme", null, null, null, fullFooter()));

        assertEquals("Acme Ltd", r.footer().companyName());
        assertEquals("1 Main St, Erode", r.footer().address());
        assertEquals("hr@acme.com", r.footer().contactEmail());
        assertEquals("+91 98765 43210", r.footer().phone());
        assertEquals("https://acme.com", r.footer().websiteUrl());
        assertEquals("(c) 2026 Acme", r.footer().copyrightText());
        assertEquals("We are an equal opportunity employer", r.footer().customText());
    }

    @Test
    void emptyFooterFieldsAreStoredAsNullNotAsEmptyText() {
        ClientResponse r = service.createClient(request("Acme", null, null, null,
                new FooterRequest("Acme", "  ", "", null, "", "   ", null)));

        assertEquals("Acme", r.footer().companyName());
        assertNull(r.footer().address());
        assertNull(r.footer().contactEmail());
        assertNull(r.footer().phone());
        assertNull(r.footer().websiteUrl());
        assertNull(r.footer().copyrightText());
        assertNull(r.footer().customText());
    }

    @Test
    void anAdminUpdateNeverChangesTheFooterTheClientSaved() {
        existing.setFooterCompanyName("Acme Ltd");
        existing.setFooterEmail("hr@acme.com");

        ClientResponse r = service.updateClient(4L, request("Acme 2", null, null, null,
                new FooterRequest("Hacked Co", "Somewhere", "x@evil.com", null, null, null, null)));

        assertEquals("Acme 2", r.name()); // the rest of the update still applies
        assertEquals("Acme Ltd", r.footer().companyName());
        assertEquals("hr@acme.com", r.footer().contactEmail());
        assertNull(r.footer().address());
    }

    @Test
    void leavingTheFooterOutOfAnUpdateKeepsTheSavedFooter() {
        existing.setFooterCompanyName("Acme Ltd");
        existing.setFooterEmail("hr@acme.com");

        ClientResponse r = service.updateClient(4L, request("Acme 2", null, null, null, null));

        assertEquals("Acme Ltd", r.footer().companyName());
        assertEquals("hr@acme.com", r.footer().contactEmail());
    }

    @Test
    void theSavedColoursAndFooterReachThePublicPage() {
        existing.setFooterCompanyName("Acme Ltd");
        existing.setFooterWebsite("https://acme.com");
        service.updateClient(4L, request("Acme", null, "#111111", "#222222", null));

        PublicClientResponse p = PublicClientResponse.from(existing);

        assertEquals("#111111", p.headerColor());
        assertEquals("#222222", p.footerColor());
        assertEquals("Acme Ltd", p.footer().companyName());
        assertEquals("https://acme.com", p.footer().websiteUrl());
    }

    @Test
    void aNewPortalWithNoColoursOrFooterHasNullsSoThemeDefaultsApply() {
        ClientResponse r = service.createClient(request("Plain Co", null, null, null, null));

        assertNull(r.headerColor());
        assertNull(r.footerColor());
        assertNull(r.footer().companyName());
    }
}

