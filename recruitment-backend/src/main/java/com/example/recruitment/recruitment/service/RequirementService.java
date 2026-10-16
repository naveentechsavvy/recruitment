package com.example.recruitment.service;

import com.example.recruitment.dto.RequirementRequest;
import com.example.recruitment.dto.RequirementResponse;
import com.example.recruitment.entity.RequirementStatus;

import java.util.List;

public interface RequirementService {
    RequirementResponse createRequirement(Long clientId, RequirementRequest request);
    List<RequirementResponse> getRequirementsByClient(Long clientId);
    RequirementResponse getRequirement(Long id);
    RequirementResponse updateRequirement(Long id, RequirementRequest request);
    RequirementResponse updateStatus(Long id, RequirementStatus status);
}
