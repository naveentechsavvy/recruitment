package com.example.recruitment.service.impl;

import com.example.recruitment.dto.HiringManagerRequest;
import com.example.recruitment.dto.HiringManagerResponse;
import com.example.recruitment.entity.Client;
import com.example.recruitment.entity.HiringManager;
import com.example.recruitment.exception.ResourceNotFoundException;
import com.example.recruitment.repository.ClientRepository;
import com.example.recruitment.repository.HiringManagerRepository;
import com.example.recruitment.service.PortalGuard;
import com.example.recruitment.service.HiringManagerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/** Business rules for hiring managers. Every public method runs inside one database transaction. */
@Service
@Transactional
public class HiringManagerServiceImpl implements HiringManagerService {

    private final HiringManagerRepository hiringManagerRepository;
    private final ClientRepository clientRepository;

    public HiringManagerServiceImpl(HiringManagerRepository hiringManagerRepository, ClientRepository clientRepository) {
        this.hiringManagerRepository = hiringManagerRepository;
        this.clientRepository = clientRepository;
    }

    @Override
    public HiringManagerResponse createHiringManager(Long clientId, HiringManagerRequest request) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id " + clientId));
        PortalGuard.requireActive(client);
        String email = normalizeEmail(request.email());
        if (hiringManagerRepository.existsByClientIdAndEmail(clientId, email)) {
            throw new IllegalArgumentException("A hiring manager with this email already exists for this client");
        }
        HiringManager manager = new HiringManager();
        manager.setClient(client);
        apply(manager, request);
        return HiringManagerResponse.from(hiringManagerRepository.save(manager));
    }

    @Override
    @Transactional(readOnly = true)
    public List<HiringManagerResponse> getHiringManagersByClient(Long clientId) {
        if (!clientRepository.existsById(clientId)) {
            throw new ResourceNotFoundException("Client not found with id " + clientId);
        }
        return hiringManagerRepository.findByClientIdOrderByCreatedAtDesc(clientId).stream()
                .map(HiringManagerResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public HiringManagerResponse getHiringManager(Long id) {
        return HiringManagerResponse.from(findManager(id));
    }

    @Override
    public HiringManagerResponse updateHiringManager(Long id, HiringManagerRequest request) {
        HiringManager manager = findManager(id);
        PortalGuard.requireActive(manager.getClient());
        String email = normalizeEmail(request.email());
        if (hiringManagerRepository.existsByClientIdAndEmailAndIdNot(manager.getClient().getId(), email, id)) {
            throw new IllegalArgumentException("A hiring manager with this email already exists for this client");
        }
        apply(manager, request); // changes are saved automatically when the transaction commits
        return HiringManagerResponse.from(manager);
    }

    private void apply(HiringManager manager, HiringManagerRequest request) {
        manager.setName(request.name().trim());
        manager.setEmail(normalizeEmail(request.email()));
        manager.setPhone(blankToNull(request.phone()));
        manager.setDepartment(blankToNull(request.department()));
        if (request.status() != null) manager.setStatus(request.status());
    }

    private HiringManager findManager(Long id) {
        return hiringManagerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hiring manager not found with id " + id));
    }

    private static String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
