package com.example.recruitment.controller;

import com.example.recruitment.dto.HiringManagerResponse;
import com.example.recruitment.dto.RequirementRequest;
import com.example.recruitment.dto.RequirementResponse;
import com.example.recruitment.dto.RequirementStatusRequest;
import com.example.recruitment.exception.ResourceNotFoundException;
import com.example.recruitment.security.AuthUser;
import com.example.recruitment.service.HiringManagerService;
import com.example.recruitment.service.RequirementService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * Hiring features for a client's OWN website (requirements now, candidates next).
 * Who may call these is decided in SecurityConfig (/api/site/**). For now every client login may.
 *
 * Client separation: the client always comes from the logged-in user (the token), never from the URL or the body.
 * A requirement that belongs to another client is answered with 404, exactly as if it did not exist.
 * The existing business rules (frozen portal, unique job id, hiring manager must belong to the client)
 * live in RequirementService and are reused unchanged.
 */
@RestController
@RequestMapping("/api/site")
public class SiteHiringController {

    private final RequirementService requirements;
    private final HiringManagerService managers;

    public SiteHiringController(RequirementService requirements, HiringManagerService managers) {
        this.requirements = requirements;
        this.managers = managers;
    }

    @GetMapping("/requirements")
    public List<RequirementResponse> list(@AuthenticationPrincipal AuthUser user) {
        return requirements.getRequirementsByClient(clientOf(user));
    }

    @GetMapping("/requirements/{id}")
    public RequirementResponse getOne(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        return own(user, id);
    }

    @PostMapping("/requirements")
    public ResponseEntity<RequirementResponse> create(@AuthenticationPrincipal AuthUser user,
                                                      @Valid @RequestBody RequirementRequest request) {
        RequirementResponse created = requirements.createRequirement(clientOf(user), request);
        return ResponseEntity.created(URI.create("/api/site/requirements/" + created.id())).body(created);
    }

    @PutMapping("/requirements/{id}")
    public RequirementResponse update(@PathVariable Long id, @AuthenticationPrincipal AuthUser user,
                                      @Valid @RequestBody RequirementRequest request) {
        own(user, id); // 404 unless it belongs to this client
        return requirements.updateRequirement(id, request);
    }

    @PatchMapping("/requirements/{id}/status")
    public RequirementResponse updateStatus(@PathVariable Long id, @AuthenticationPrincipal AuthUser user,
                                            @Valid @RequestBody RequirementStatusRequest request) {
        own(user, id);
        return requirements.updateStatus(id, request.status());
    }

    /** Needed by the requirement form: a requirement is owned by one of the client's hiring managers. */
    @GetMapping("/hiring-managers")
    public List<HiringManagerResponse> hiringManagers(@AuthenticationPrincipal AuthUser user) {
        return managers.getHiringManagersByClient(clientOf(user));
    }

    private static Long clientOf(AuthUser user) {
        if (user == null || user.clientId() == null) {
            throw new ResourceNotFoundException("No website is linked to this login");
        }
        return user.clientId();
    }

    /** The requirement, only if it belongs to the logged-in user's client. */
    private RequirementResponse own(AuthUser user, Long id) {
        Long clientId = clientOf(user);
        RequirementResponse r = requirements.getRequirement(id);
        if (!clientId.equals(r.clientId())) {
            throw new ResourceNotFoundException("Requirement not found with id " + id);
        }
        return r;
    }
}