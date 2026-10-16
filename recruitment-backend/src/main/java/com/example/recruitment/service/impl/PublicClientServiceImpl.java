package com.example.recruitment.service.impl;

import com.example.recruitment.dto.PublicClientResponse;
import com.example.recruitment.dto.PublicJobResponse;
import com.example.recruitment.entity.Client;
import com.example.recruitment.entity.ClientStatus;
import com.example.recruitment.entity.RequirementStatus;
import com.example.recruitment.exception.ResourceNotFoundException;
import com.example.recruitment.repository.ClientRepository;
import com.example.recruitment.repository.RequirementRepository;
import com.example.recruitment.service.PublicClientService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/** Only clients whose application has been generated (status Ready or Active) are public. Drafts return 404. */
@Service
@Transactional(readOnly = true)
public class PublicClientServiceImpl implements PublicClientService {

    private final ClientRepository clientRepository;
    private final RequirementRepository requirementRepository;

    public PublicClientServiceImpl(ClientRepository clientRepository, RequirementRepository requirementRepository) {
        this.clientRepository = clientRepository;
        this.requirementRepository = requirementRepository;
    }

    @Override
    public PublicClientResponse getApplication(String slug) {
        return PublicClientResponse.from(findPublished(slug));
    }

    @Override
    public List<PublicJobResponse> getOpenJobs(String slug) {
        Client client = findPublished(slug);
        if (client.isFrozen()) return List.of(); // a frozen portal takes no new applications
        // Scoped by the client's id, so one client never sees another client's jobs.
        return requirementRepository
                .findByClientIdAndStatusOrderByCreatedAtDesc(client.getId(), RequirementStatus.OPEN)
                .stream().map(PublicJobResponse::from).toList();
    }

    private Client findPublished(String slug) {
        return clientRepository.findBySlug(slug.trim().toLowerCase(Locale.ROOT))
                .filter(c -> c.getStatus() != ClientStatus.DRAFT)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
    }
}
