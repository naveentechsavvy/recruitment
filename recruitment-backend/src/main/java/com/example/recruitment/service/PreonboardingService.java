package com.example.recruitment.service;

import com.example.recruitment.dto.PreonboardingCaseView;
import com.example.recruitment.dto.PreonboardingItemRequest;
import com.example.recruitment.dto.PreonboardingItemView;
import com.example.recruitment.dto.PreonboardingSettingsRequest;
import com.example.recruitment.dto.PreonboardingSettingsView;
import com.example.recruitment.entity.Candidate;
import com.example.recruitment.entity.CandidateEvent;
import com.example.recruitment.entity.Client;
import com.example.recruitment.entity.EventType;
import com.example.recruitment.entity.PreonboardingCase;
import com.example.recruitment.entity.PreonboardingConfig;
import com.example.recruitment.entity.PreonboardingItem;
import com.example.recruitment.entity.PreonboardingSetting;
import com.example.recruitment.entity.PreonboardingStatus;
import com.example.recruitment.repository.PreonboardingAccessRepository;
import com.example.recruitment.repository.PreonboardingDocumentRepository;
import com.example.recruitment.entity.Role;
import com.example.recruitment.exception.ResourceNotFoundException;
import com.example.recruitment.repository.CandidateEventRepository;
import com.example.recruitment.repository.ClientRepository;
import com.example.recruitment.repository.PreonboardingCaseRepository;
import com.example.recruitment.repository.PreonboardingConfigRepository;
import com.example.recruitment.repository.PreonboardingSettingRepository;
import com.example.recruitment.security.AuthUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pre-onboarding, the part before the candidate's own page: what each client asks for, and the case that is created
 * automatically when a candidate accepts the offer.
 *
 * Everything belongs to a client. The client always comes from the logged-in user, never from the request.
 * Only an admin, an HR BP or a recruiter may look at or change it. Hiring Manager and Manager have no access.
 */
@Service
public class PreonboardingService {

    public static final int DEFAULT_SUBMISSION_DAYS = 5;
    public static final int MAX_SUBMISSION_DAYS = 60;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMMM yyyy");

    private final PreonboardingSettingRepository settings;
    private final PreonboardingConfigRepository configs;
    private final PreonboardingCaseRepository cases;
    private final ClientRepository clients;
    private final CandidateEventRepository events;
    @Autowired(required = false)
    private PreonboardingAccessService accessService;
    @Autowired(required = false)
    private PreonboardingAccessRepository accessRepository;
    @Autowired(required = false)
    private PreonboardingDocumentRepository documentRepository;
    @Autowired(required = false)
    private PreonboardingWorkflowService workflowService;

    public PreonboardingService(PreonboardingSettingRepository settings,
                                PreonboardingConfigRepository configs,
                                PreonboardingCaseRepository cases,
                                ClientRepository clients,
                                CandidateEventRepository events) {
        this.settings = settings;
        this.configs = configs;
        this.cases = cases;
        this.clients = clients;
        this.events = events;
    }

    // ---------------------------------------------------------------- the client's settings

    /** Not read-only: a client that has never opened the page gets its default settings saved on first use. */
    @Transactional
    public PreonboardingSettingsView getSettings(AuthUser user) {
        requireStaff(user);
        return view(own(user));
    }

    @Transactional
    public PreonboardingSettingsView updateSettings(AuthUser user, PreonboardingSettingsRequest request) {
        requireStaff(user);
        Client client = own(user);
        PortalGuard.requireActive(client); // a frozen portal takes no changes

        if (request == null) throw new IllegalArgumentException("Nothing to save");
        if (request.submissionDays() < 1 || request.submissionDays() > MAX_SUBMISSION_DAYS) {
            throw new IllegalArgumentException("Days to submit must be between 1 and " + MAX_SUBMISSION_DAYS);
        }

        Map<String, PreonboardingSetting> byCode = ensureDefaults(client);
        if (request.items() != null) {
            for (PreonboardingItemRequest item : request.items()) {
                PreonboardingItem known = PreonboardingItem.find(item == null ? null : item.code())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Unknown item: " + (item == null ? null : item.code())));
                PreonboardingSetting row = byCode.get(known.name());
                row.setEnabled(item.enabled());
                row.setMandatory(item.enabled() && item.mandatory()); // an item that is switched off cannot be required
            }
        }

        config(client).setSubmissionDays(request.submissionDays());
        return view(client);
    }

    // ---------------------------------------------------------------- the case

    /**
     * Creates the pre-onboarding case of a candidate who accepted the offer. Safe to call twice: a candidate
     * never gets a second case. The days allowed come from the client's setting.
     */
    @Transactional
    public PreonboardingCase createFor(Candidate candidate) {
        Optional<PreonboardingCase> existing = candidate.getId() == null
                ? Optional.empty() : cases.findByCandidateId(candidate.getId());
        if (existing.isPresent()) return existing.get();

        Client client = candidate.getClient();
        ensureDefaults(client); // the public candidate form must have defaults even if HR has not opened settings yet
        Instant due = Instant.now().plus(config(client).getSubmissionDays(), ChronoUnit.DAYS);

        PreonboardingCase created = new PreonboardingCase();
        created.setCandidate(candidate);
        created.setClient(client);
        created.setStatus(PreonboardingStatus.CREATED);
        created.setDueAt(due);
        cases.save(created);

        CandidateEvent e = new CandidateEvent();
        e.setCandidate(candidate);
        e.setType(EventType.STAGE_DECISION);
        e.setTitle("Pre-onboarding case created");
        e.setDetails("Documents are due by " + DATE.format(due.atZone(ZoneOffset.UTC)) + ".");
        e.setActor("System");
        events.save(e);
        // The candidate receives their secure invitation immediately after accepting the offer.
        // Unit tests that construct this service directly do not wire the mail-backed collaborator.
        if (accessService != null) accessService.inviteSystem(candidate);

        return created;
    }

    /** The case of a candidate, or null when there is none. The caller has already checked the candidate belongs to the user's client. */
    @Transactional(readOnly = true)
    public PreonboardingCaseView caseFor(Long candidateId, AuthUser user) {
        requireStaff(user);
        return cases.findByCandidateId(candidateId)
                .map(c -> new PreonboardingCaseView(c.getId(), c.getStatus(), c.getCreatedAt(), c.getDueAt()))
                .orElse(null);
    }

    /** Called only when a candidate is deleted. */
    @Transactional
    public void deleteForCandidate(Long candidateId) {
        cases.findByCandidateId(candidateId).ifPresent(c -> {
            if (workflowService != null) workflowService.deleteFilesForCase(c.getId());
            if (documentRepository != null) documentRepository.deleteByPreonboardingCaseId(c.getId());
            if (accessRepository != null) accessRepository.deleteByPreonboardingCaseId(c.getId());
        });
        cases.deleteByCandidateId(candidateId);
    }

    // ---------------------------------------------------------------- helpers

    private void requireStaff(AuthUser user) {
        if (user == null || (user.role() != Role.ADMIN && user.role() != Role.HR_BP && user.role() != Role.RECRUITER)) {
            throw new AccessDeniedException("Only an HR BP or a recruiter can use pre-onboarding");
        }
    }

    private Client own(AuthUser user) {
        if (user.clientId() == null) throw new ResourceNotFoundException("No website is linked to this login");
        return clients.findById(user.clientId())
                .orElseThrow(() -> new ResourceNotFoundException("Website not found"));
    }

    private PreonboardingSettingsView view(Client client) {
        Map<String, PreonboardingSetting> byCode = ensureDefaults(client);
        List<PreonboardingItemView> items = java.util.Arrays.stream(PreonboardingItem.values())
                .map(i -> {
                    PreonboardingSetting s = byCode.get(i.name());
                    return new PreonboardingItemView(i.name(), i.getKind().name(), i.getLabel(), s.isEnabled(), s.isMandatory());
                })
                .toList();
        return new PreonboardingSettingsView(config(client).getSubmissionDays(), items);
    }

    /** Every item of the fixed list has a row for this client. Missing rows are added with the default choice. */
    private Map<String, PreonboardingSetting> ensureDefaults(Client client) {
        Map<String, PreonboardingSetting> byCode = new HashMap<>();
        for (PreonboardingSetting s : settings.findByClientId(client.getId())) byCode.put(s.getItemCode(), s);

        for (PreonboardingItem item : PreonboardingItem.values()) {
            if (!byCode.containsKey(item.name())) {
                PreonboardingSetting row = new PreonboardingSetting(client, item.name(), true, item.isDefaultMandatory());
                settings.save(row);
                byCode.put(item.name(), row);
            }
        }
        return byCode;
    }

    private PreonboardingConfig config(Client client) {
        return configs.findByClientId(client.getId())
                .orElseGet(() -> configs.save(new PreonboardingConfig(client, DEFAULT_SUBMISSION_DAYS)));
    }
}
