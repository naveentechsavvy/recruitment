package com.example.recruitment.controller;

import com.example.recruitment.dto.RequirementRequest;
import com.example.recruitment.dto.RequirementResponse;
import com.example.recruitment.dto.RequirementStatusRequest;
import com.example.recruitment.security.AuthUser;
import com.example.recruitment.security.ClientAccess;
import com.example.recruitment.service.RequirementService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
public class RequirementController {

    private final RequirementService requirementService;

    public RequirementController(RequirementService requirementService) {
        this.requirementService = requirementService;
    }

    @PostMapping("/clients/{clientId}/requirements")
    public ResponseEntity<RequirementResponse> create(@PathVariable Long clientId,
                                                      @Valid @RequestBody RequirementRequest request) {
        RequirementResponse created = requirementService.createRequirement(clientId, request);
        return ResponseEntity.created(URI.create("/api/requirements/" + created.id())).body(created); // 201
    }

    @GetMapping("/clients/{clientId}/requirements")
    public List<RequirementResponse> getByClient(@PathVariable Long clientId) {
        return requirementService.getRequirementsByClient(clientId);
    }

    @GetMapping("/requirements/{id}")
    public RequirementResponse getOne(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        RequirementResponse response = requirementService.getRequirement(id);
        ClientAccess.require(me, response.clientId()); // a hiring manager only sees his own client's jobs
        return response;
    }

    @PutMapping("/requirements/{id}")
    public RequirementResponse update(@PathVariable Long id, @Valid @RequestBody RequirementRequest request) {
        return requirementService.updateRequirement(id, request);
    }

    @PatchMapping("/requirements/{id}/status")
    public RequirementResponse updateStatus(@PathVariable Long id, @Valid @RequestBody RequirementStatusRequest request) {
        return requirementService.updateStatus(id, request.status());
    }
}
