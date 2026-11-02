package com.example.recruitment.service;

import com.example.recruitment.dto.PreonboardingCaseView;
import com.example.recruitment.dto.PreonboardingItemRequest;
import com.example.recruitment.dto.PreonboardingItemView;
import com.example.recruitment.dto.PreonboardingSettingsRequest;
import com.example.recruitment.dto.PreonboardingSettingsView;
import com.example.recruitment.entity.Candidate;
import com.example.recruitment.entity.CandidateEvent;
import com.example.recruitment.entity.Client;
import com.example.recruitment.entity.PreonboardingCase;
import com.example.recruitment.entity.PreonboardingConfig;
import com.example.recruitment.entity.PreonboardingItem;
import com.example.recruitment.entity.PreonboardingSetting;
import com.example.recruitment.entity.PreonboardingStatus;
import com.example.recruitment.entity.Role;
import com.example.recruitment.exception.PortalFrozenException;
import com.example.recruitment.repository.CandidateEventRepository;
import com.example.recruitment.repository.ClientRepository;
import com.example.recruitment.repository.PreonboardingCaseRepository;
import com.example.recruitment.repository.PreonboardingConfigRepository;
import com.example.recruitment.repository.PreonboardingSettingRepository;
import com.example.recruitment.security.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PreonboardingServiceTest {

    PreonboardingSettingRepository settingRepo = mock(PreonboardingSettingRepository.class);
    PreonboardingConfigRepository configRepo = mock(PreonboardingConfigRepository.class);
    PreonboardingCaseRepository caseRepo = mock(PreonboardingCaseRepository.class);
    ClientRepository clients = mock(ClientRepository.class);
    CandidateEventRepository events = mock(CandidateEventRepository.class);

    PreonboardingService service;
    Client client;
    Candidate candidate;
    List<PreonboardingSetting> savedSettings = new ArrayList<>();
    List<PreonboardingCase> savedCases = new ArrayList<>();
    List<CandidateEvent> savedEvents = new ArrayList<>();
    PreonboardingConfig config;

    final AuthUser hrBp = new AuthUser(5L, "hrbp@example.com", Role.HR_BP, 1L);
    final AuthUser recruiter = new AuthUser(1L, "rec@example.com", Role.RECRUITER, 1L);
    final AuthUser hiringManager = new AuthUser(2L, "hm@example.com", Role.HIRING_MANAGER, 1L);
    final AuthUser manager = new AuthUser(3L, "mgr@example.com", Role.MANAGER, 1L);

    @BeforeEach
    void setUp() {
        service = new PreonboardingService(settingRepo, configRepo, caseRepo, clients, events);

        client = new Client();
        ReflectionTestUtils.setField(client, "id", 1L);
        candidate = new Candidate();
        ReflectionTestUtils.setField(candidate, "id", 100L);
        candidate.setClient(client);

        when(clients.findById(1L)).thenReturn(Optional.of(client));
        // the "table": what was saved is what the next find returns
        when(settingRepo.findByClientId(1L)).thenAnswer(inv -> List.copyOf(savedSettings));
        when(settingRepo.save(any(PreonboardingSetting.class))).thenAnswer(inv -> {
            PreonboardingSetting s = inv.getArgument(0);
            savedSettings.add(s);
            return s;
        });
        when(configRepo.findByClientId(1L)).thenAnswer(inv -> Optional.ofNullable(config));
        when(configRepo.save(any(PreonboardingConfig.class))).thenAnswer(inv -> {
            config = inv.getArgument(0);
            return config;
        });
        when(caseRepo.findByCandidateId(100L)).thenAnswer(inv -> savedCases.stream().findFirst());
        when(caseRepo.save(any(PreonboardingCase.class))).thenAnswer(inv -> {
            PreonboardingCase c = inv.getArgument(0);
            savedCases.add(c);
            return c;
        });
        when(events.save(any(CandidateEvent.class))).thenAnswer(inv -> {
            CandidateEvent e = inv.getArgument(0);
            savedEvents.add(e);
            return e;
        });
    }

    private static PreonboardingItemView item(PreonboardingSettingsView v, PreonboardingItem i) {
        return v.items().stream().filter(x -> x.code().equals(i.name())).findFirst().orElseThrow();
    }

    @Test
    void aClientThatNeverSetItUpGetsTheDefaults() {
        PreonboardingSettingsView v = service.getSettings(hrBp);

        assertEquals(PreonboardingService.DEFAULT_SUBMISSION_DAYS, v.submissionDays());
        assertEquals(PreonboardingItem.values().length, v.items().size());
        assertTrue(item(v, PreonboardingItem.PAN_CARD).mandatory());
        assertTrue(item(v, PreonboardingItem.PAN_CARD).enabled());
        assertFalse(item(v, PreonboardingItem.PASSPORT_PHOTO).mandatory());
        assertEquals("DOCUMENT", item(v, PreonboardingItem.PAN_CARD).kind());
        assertEquals("DETAIL", item(v, PreonboardingItem.DATE_OF_BIRTH).kind());
    }

    @Test
    void settingsCanBeChanged() {
        service.getSettings(hrBp);

        PreonboardingSettingsView v = service.updateSettings(hrBp, new PreonboardingSettingsRequest(10, List.of(
                new PreonboardingItemRequest("PASSPORT_PHOTO", true, true),
                new PreonboardingItemRequest("PAN_CARD", false, true))));

        assertEquals(10, v.submissionDays());
        assertTrue(item(v, PreonboardingItem.PASSPORT_PHOTO).mandatory());
        assertFalse(item(v, PreonboardingItem.PAN_CARD).enabled());
        assertFalse(item(v, PreonboardingItem.PAN_CARD).mandatory(), "an item that is switched off cannot be required");
        assertTrue(item(v, PreonboardingItem.ID_PROOF).mandatory(), "items that were not sent stay as they were");
    }

    @Test
    void badSettingsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> service.updateSettings(hrBp, new PreonboardingSettingsRequest(0, List.of())));
        assertThrows(IllegalArgumentException.class, () -> service.updateSettings(hrBp, new PreonboardingSettingsRequest(61, List.of())));
        assertThrows(IllegalArgumentException.class, () -> service.updateSettings(hrBp,
                new PreonboardingSettingsRequest(5, List.of(new PreonboardingItemRequest("SALARY_SLIP", true, true)))));
        assertThrows(IllegalArgumentException.class, () -> service.updateSettings(hrBp, null));
    }

    @Test
    void aFrozenPortalTakesNoChanges() {
        client.setFrozen(true);
        assertThrows(PortalFrozenException.class, () -> service.updateSettings(hrBp, new PreonboardingSettingsRequest(5, List.of())));
    }

    @Test
    void onlyAdminHrBpAndRecruiterHaveAccess() {
        assertDoesNotThrow(() -> service.getSettings(hrBp));
        assertDoesNotThrow(() -> service.getSettings(recruiter));
        assertThrows(AccessDeniedException.class, () -> service.getSettings(hiringManager));
        assertThrows(AccessDeniedException.class, () -> service.getSettings(manager));
        assertThrows(AccessDeniedException.class, () -> service.updateSettings(manager, new PreonboardingSettingsRequest(5, List.of())));
        assertThrows(AccessDeniedException.class, () -> service.caseFor(100L, hiringManager));
        assertThrows(AccessDeniedException.class, () -> service.getSettings(null));
    }

    @Test
    void aLoginWithoutAWebsiteHasNoSettings() {
        AuthUser nobody = new AuthUser(7L, "x@example.com", Role.HR_BP, null);
        assertThrows(com.example.recruitment.exception.ResourceNotFoundException.class, () -> service.getSettings(nobody));
    }

    @Test
    void theCaseIsCreatedOnceWithTheClientsDeadline() {
        service.getSettings(hrBp);
        service.updateSettings(hrBp, new PreonboardingSettingsRequest(7, List.of()));

        PreonboardingCase created = service.createFor(candidate);

        assertEquals(PreonboardingStatus.CREATED, created.getStatus());
        assertEquals(client, created.getClient());
        assertTrue(created.getDueAt().isAfter(Instant.now().plus(6, ChronoUnit.DAYS)));
        assertTrue(created.getDueAt().isBefore(Instant.now().plus(8, ChronoUnit.DAYS)));
        assertTrue(savedEvents.stream().anyMatch(e -> "Pre-onboarding case created".equals(e.getTitle())));

        // a second call gives back the same case and creates nothing new
        assertSame(created, service.createFor(candidate));
        assertEquals(1, savedCases.size());
    }

    @Test
    void aCaseWorksEvenIfTheClientNeverOpenedTheSettings() {
        PreonboardingCase created = service.createFor(candidate);

        assertNotNull(created.getDueAt());
        assertEquals(PreonboardingService.DEFAULT_SUBMISSION_DAYS, config.getSubmissionDays());
    }

    @Test
    void theHiringTeamCanSeeTheCase() {
        assertNull(service.caseFor(100L, hrBp), "no case yet");
        service.createFor(candidate);

        PreonboardingCaseView view = service.caseFor(100L, hrBp);

        assertEquals(PreonboardingStatus.CREATED, view.status());
        assertNotNull(view.dueAt());
    }

    @Test
    void deletingACandidateRemovesTheirCase() {
        service.deleteForCandidate(100L);
        verify(caseRepo).deleteByCandidateId(100L);
        verify(caseRepo, never()).save(any(PreonboardingCase.class));
    }
}
