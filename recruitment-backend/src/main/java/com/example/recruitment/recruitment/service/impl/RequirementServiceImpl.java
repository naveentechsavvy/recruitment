package com.example.recruitment.service.impl;

import com.example.recruitment.dto.RequirementRequest;
import com.example.recruitment.dto.RequirementResponse;
import com.example.recruitment.entity.Client;
import com.example.recruitment.entity.HiringManager;
import com.example.recruitment.entity.Requirement;
import com.example.recruitment.entity.RequirementStatus;
import com.example.recruitment.exception.ResourceNotFoundException;
import com.example.recruitment.repository.ClientRepository;
import com.example.recruitment.repository.HiringManagerRepository;
import com.example.recruitment.repository.RequirementRepository;
import com.example.recruitment.service.PortalGuard;
import com.example.recruitment.service.RequirementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Business rules for requirements. Every query is scoped by clientId so clients never see each other's data. */
@Service
@Transactional
public class RequirementServiceImpl implements RequirementService {

    private final RequirementRepository requirementRepository;
    private final HiringManagerRepository hiringManagerRepository;
    private final ClientRepository clientRepository;

    public RequirementServiceImpl(RequirementRepository requirementRepository,
                                  HiringManagerRepository hiringManagerRepository,
                                  ClientRepository clientRepository) {
        this.requirementRepository = requirementRepository;
        this.hiringManagerRepository = hiringManagerRepository;
        this.clientRepository = clientRepository;
    }

    @Override
    public RequirementResponse createRequirement(Long clientId, RequirementRequest request) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id " + clientId));
        PortalGuard.requireActive(client);
        String jobId = request.jobId().trim();
        if (requirementRepository.existsByClientIdAndJobId(clientId, jobId)) {
            throw new IllegalArgumentException("Job ID " + jobId + " already exists for this client");
        }
        Requirement requirement = new Requirement();
        requirement.setClient(client);
        apply(requirement, request, findManagerOfClient(request.hiringManagerId(), clientId));
        return RequirementResponse.from(requirementRepository.save(requirement));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RequirementResponse> getRequirementsByClient(Long clientId) {
        if (!clientRepository.existsById(clientId)) {
            throw new ResourceNotFoundException("Client not found with id " + clientId);
        }
        return requirementRepository.findByClientIdOrderByCreatedAtDesc(clientId).stream()
                .map(RequirementResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RequirementResponse getRequirement(Long id) {
        return RequirementResponse.from(findRequirement(id));
    }

    @Override
    public RequirementResponse updateRequirement(Long id, RequirementRequest request) {
        Requirement requirement = findRequirement(id);
        PortalGuard.requireActive(requirement.getClient());
        Long clientId = requirement.getClient().getId(); // the client never changes
        String jobId = request.jobId().trim();
        if (requirementRepository.existsByClientIdAndJobIdAndIdNot(clientId, jobId, id)) {
            throw new IllegalArgumentException("Job ID " + jobId + " already exists for this client");
        }
        apply(requirement, request, findManagerOfClient(request.hiringManagerId(), clientId));
        return RequirementResponse.from(requirement);
    }

    @Override
    public RequirementResponse updateStatus(Long id, RequirementStatus status) {
        Requirement requirement = findRequirement(id);
        PortalGuard.requireActive(requirement.getClient());
        requirement.setStatus(status);
        return RequirementResponse.from(requirement);
    }

    private void apply(Requirement requirement, RequirementRequest request, HiringManager manager) {
        if (request.maxExperience() < request.minExperience()) {
            throw new IllegalArgumentException("Maximum experience cannot be less than minimum experience");
        }
        requirement.setHiringManager(manager);
        requirement.setJobId(request.jobId().trim());
        requirement.setJobTitle(request.jobTitle().trim());
        requirement.setNumberOfPositions(request.numberOfPositions());
        requirement.setJobDescription(request.jobDescription().trim());
        requirement.setRequiredSkills(request.requiredSkills().trim());
        requirement.setMinExperience(request.minExperience());
        requirement.setMaxExperience(request.maxExperience());
        requirement.setLocation(request.location().trim());
        requirement.setDepartment(request.department() == null || request.department().isBlank()
                ? null : request.department().trim());
        if (request.employmentType() != null) requirement.setEmploymentType(request.employmentType());
        if (request.priority() != null) requirement.setPriority(request.priority());
        if (request.status() != null) requirement.setStatus(request.status());
    }

    /** A requirement may only be owned by a hiring manager of the same client. */
    private HiringManager findManagerOfClient(Long hiringManagerId, Long clientId) {
        return hiringManagerRepository.findByIdAndClientId(hiringManagerId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Hiring manager not found for this client"));
    }

    private Requirement findRequirement(Long id) {
        return requirementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Requirement not found with id " + id));
    }
}
