package com.example.recruitment.service.impl;

import com.example.recruitment.dto.ClientProcessRequest;
import com.example.recruitment.dto.ClientProcessResponse;
import com.example.recruitment.dto.ClientRequest;
import com.example.recruitment.dto.ClientResponse;
import com.example.recruitment.dto.FooterRequest;
import com.example.recruitment.entity.Client;
import com.example.recruitment.entity.ClientProcess;
import com.example.recruitment.entity.RecruitmentProcess;
import com.example.recruitment.exception.ResourceNotFoundException;
import com.example.recruitment.repository.ClientProcessRepository;
import com.example.recruitment.repository.ClientRepository;
import com.example.recruitment.repository.RecruitmentProcessRepository;
import com.example.recruitment.service.ClientService;
import com.example.recruitment.service.ClientSlugGenerator;
import com.example.recruitment.service.FileStorageService;
import com.example.recruitment.service.ResumeStorageService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** All business rules live here. Every public method runs inside one database transaction. */
@Service
@Transactional
public class ClientServiceImpl implements ClientService {

    private final ClientRepository clientRepository;
    private final ClientProcessRepository clientProcessRepository;
    private final RecruitmentProcessRepository processRepository;
    private final FileStorageService fileStorage;
    private final ClientSlugGenerator slugGenerator;
    private final ResumeStorageService resumeStorage;

    @PersistenceContext
    private EntityManager em;

    public ClientServiceImpl(ClientRepository clientRepository,
                             ClientProcessRepository clientProcessRepository,
                             RecruitmentProcessRepository processRepository,
                             FileStorageService fileStorage,
                             ClientSlugGenerator slugGenerator,
                             ResumeStorageService resumeStorage) {
        this.clientRepository = clientRepository;
        this.clientProcessRepository = clientProcessRepository;
        this.processRepository = processRepository;
        this.fileStorage = fileStorage;
        this.slugGenerator = slugGenerator;
        this.resumeStorage = resumeStorage;
    }

    // ---------- clients ----------

    @Override
    public ClientResponse createClient(ClientRequest request) {
        Client client = new Client();
        apply(client, request);
        // The footer is set ONCE here, as the starting values. After this only the client can change it (see SiteService).
        if (request.footer() != null) applyFooter(client, request.footer());
        client.setSlug(slugGenerator.uniqueSlug(client.getName())); // the public link; stays the same if the name changes later
        return ClientResponse.from(clientRepository.save(client));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClientResponse> getAllClients() {
        return clientRepository.findAllByOrderByCreatedAtDesc().stream().map(ClientResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ClientResponse getClient(Long id) {
        return ClientResponse.from(findClient(id));
    }

    @Override
    public ClientResponse updateClient(Long id, ClientRequest request) {
        Client client = findClient(id);
        apply(client, request); // changes are saved automatically when the transaction commits. The footer is NOT changed here.
        if (client.getSlug() == null) client.setSlug(slugGenerator.uniqueSlug(client.getName()));
        return ClientResponse.from(client);
    }

    /**
     * Deletes the client AND everything that belongs to it: job requirements, hiring managers
     * and workflow stages. The order matters: children before parents.
     * The logo file is removed only after the database commit succeeds.
     *
     * TODO (when the Candidate module is added): delete candidate history, candidates and their
     * resume files here, BEFORE the Requirement delete, e.g.
     *   select c.resumeFile from Candidate c where c.client.id = :id   (collect resume files)
     *   delete from CandidateHistory h where h.candidate.id in (select c.id from Candidate c where c.client.id = :id)
     *   delete from Candidate c where c.client.id = :id
     * and call resumeStorage.delete(...) for each file in afterCommit().
     */
    @Override
    public void deleteClient(Long id) {
        Client client = findClient(id);
        String logoUrl = client.getLogoUrl();

        // Candidates first (children before parents), and remember their resume files to delete after commit.
        List<String> resumeFiles = em.createQuery(
                "select c.resumeFile from Candidate c where c.client.id = :id and c.resumeFile is not null", String.class)
                .setParameter("id", id).getResultList();
        em.createQuery("delete from CandidateEvent e where e.candidate.id in (select c.id from Candidate c where c.client.id = :id)")
                .setParameter("id", id).executeUpdate();
        em.createQuery("delete from CandidateStageRecord s where s.candidate.id in (select c.id from Candidate c where c.client.id = :id)")
                .setParameter("id", id).executeUpdate();
        em.createQuery("delete from Candidate c where c.client.id = :id").setParameter("id", id).executeUpdate();

        em.createQuery("delete from Requirement r where r.client.id = :id").setParameter("id", id).executeUpdate();
        em.createQuery("delete from HiringManager m where m.client.id = :id").setParameter("id", id).executeUpdate();
        em.createQuery("delete from ClientRoleAssignment a where a.clientId = :id").setParameter("id", id).executeUpdate(); // role assignments of this portal
        em.createQuery("delete from AppUser u where u.clientId = :id").setParameter("id", id).executeUpdate(); // logins of this client's hiring managers
        clientRepository.delete(client); // also deletes its client_processes rows (cascade)

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                fileStorage.delete(logoUrl);
                resumeFiles.forEach(resumeStorage::delete);
            }
        });
    }

    private void apply(Client client, ClientRequest request) {
        client.setName(request.name().trim());
        client.setDescription(request.description());
        client.setApplicationName(request.applicationName());
        if (request.theme() != null) client.setTheme(request.theme());
        if (request.status() != null) client.setStatus(request.status());
        // Colours: left out = keep what is saved. An empty colour clears it (back to the theme's colour).
        // The footer is not handled here on purpose: the admin sets it only when creating the client, then only the client edits it.
        if (request.headerColor() != null) client.setHeaderColor(normalizeColor(request.headerColor()));
        if (request.footerColor() != null) client.setFooterColor(normalizeColor(request.footerColor()));
    }

    private static void applyFooter(Client client, FooterRequest f) {
        client.setFooterCompanyName(blankToNull(f.companyName()));
        client.setFooterAddress(blankToNull(f.address()));
        client.setFooterEmail(blankToNull(f.contactEmail()));
        client.setFooterPhone(blankToNull(f.phone()));
        client.setFooterWebsite(blankToNull(f.websiteUrl()));
        client.setFooterCopyright(blankToNull(f.copyrightText()));
        client.setFooterText(blankToNull(f.customText()));
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** "#ABC" and "#aabbcc" both become "#aabbcc". Empty becomes null. The request is already validated. */
    static String normalizeColor(String color) {
        String c = color == null ? "" : color.trim().toLowerCase(Locale.ROOT);
        if (c.isEmpty()) return null;
        if (c.length() == 4) {
            c = "#" + c.charAt(1) + c.charAt(1) + c.charAt(2) + c.charAt(2) + c.charAt(3) + c.charAt(3);
        }
        return c;
    }

    // ---------- freeze / unfreeze ----------

    @Override
    public ClientResponse freezeClient(Long id, String byEmail) {
        Client client = findClient(id);
        if (client.isFrozen()) throw new IllegalArgumentException("This client portal is already frozen");
        client.setFrozen(true);
        client.setFrozenAt(Instant.now());
        client.setFrozenBy(byEmail);
        return ClientResponse.from(client);
    }

    @Override
    public ClientResponse unfreezeClient(Long id) {
        Client client = findClient(id);
        if (!client.isFrozen()) throw new IllegalArgumentException("This client portal is not frozen");
        client.setFrozen(false);
        client.setFrozenAt(null);
        client.setFrozenBy(null);
        return ClientResponse.from(client);
    }

    // ---------- logo ----------

    @Override
    public ClientResponse uploadLogo(Long id, MultipartFile file) {
        Client client = findClient(id);
        String oldUrl = client.getLogoUrl();
        client.setLogoUrl(fileStorage.saveLogo(file));
        fileStorage.delete(oldUrl);
        return ClientResponse.from(client);
    }

    @Override
    public void removeLogo(Long id) {
        Client client = findClient(id);
        fileStorage.delete(client.getLogoUrl());
        client.setLogoUrl(null);
    }

    // ---------- client process configuration ----------

    /** Replaces the client's configured processes with exactly what the request lists. */
    @Override
    public List<ClientProcessResponse> configureProcesses(Long clientId, ClientProcessRequest request) {
        Client client = findClient(clientId);

        Set<Long> requestedIds = new HashSet<>();
        for (ClientProcessRequest.Item item : request.processes()) {
            if (!requestedIds.add(item.processId())) {
                throw new IllegalArgumentException("Duplicate processId: " + item.processId());
            }
        }

        Map<Long, ClientProcess> existing = client.getClientProcesses().stream()
                .collect(Collectors.toMap(cp -> cp.getProcess().getId(), Function.identity()));

        // Remove processes that are no longer selected.
        client.getClientProcesses().removeIf(cp -> !requestedIds.contains(cp.getProcess().getId()));

        // Update existing rows in place, add new ones.
        int fallbackOrder = 1;
        for (ClientProcessRequest.Item item : request.processes()) {
            ClientProcess cp = existing.get(item.processId());
            if (cp == null) {
                RecruitmentProcess process = processRepository.findById(item.processId())
                        .orElseThrow(() -> new ResourceNotFoundException("Recruitment process not found: " + item.processId()));
                cp = new ClientProcess();
                cp.setProcess(process);
                client.addClientProcess(cp);
            }
            cp.setEnabled(item.enabled() == null || item.enabled());
            cp.setDisplayOrder(item.displayOrder() != null ? item.displayOrder() : fallbackOrder);
            fallbackOrder++;
        }

        return client.getClientProcesses().stream()
                .sorted(Comparator.comparingInt(ClientProcess::getDisplayOrder))
                .map(ClientProcessResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClientProcessResponse> getClientProcesses(Long clientId) {
        if (!clientRepository.existsById(clientId)) {
            throw new ResourceNotFoundException("Client not found with id " + clientId);
        }
        return clientProcessRepository.findByClientIdOrderByDisplayOrderAsc(clientId).stream()
                .map(ClientProcessResponse::from)
                .toList();
    }

    private Client findClient(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id " + id));
    }
}
