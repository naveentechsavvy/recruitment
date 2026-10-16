package com.example.recruitment.controller;

import com.example.recruitment.dto.HiringManagerRequest;
import com.example.recruitment.dto.HiringManagerResponse;
import com.example.recruitment.security.AuthUser;
import com.example.recruitment.security.ClientAccess;
import com.example.recruitment.service.HiringManagerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Controllers only handle HTTP: read the request, call the service, return the response. */
@RestController
@RequestMapping("/api")
public class HiringManagerController {

    private final HiringManagerService hiringManagerService;

    public HiringManagerController(HiringManagerService hiringManagerService) {
        this.hiringManagerService = hiringManagerService;
    }

    @PostMapping("/clients/{clientId}/hiring-managers")
    public ResponseEntity<HiringManagerResponse> create(@PathVariable Long clientId,
                                                        @Valid @RequestBody HiringManagerRequest request) {
        HiringManagerResponse created = hiringManagerService.createHiringManager(clientId, request);
        return ResponseEntity.created(URI.create("/api/hiring-managers/" + created.id())).body(created); // 201
    }

    @GetMapping("/clients/{clientId}/hiring-managers")
    public List<HiringManagerResponse> getByClient(@PathVariable Long clientId) {
        return hiringManagerService.getHiringManagersByClient(clientId);
    }

    @GetMapping("/hiring-managers/{id}")
    public HiringManagerResponse getOne(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        HiringManagerResponse response = hiringManagerService.getHiringManager(id);
        ClientAccess.require(me, response.clientId());
        return response;
    }

    @PutMapping("/hiring-managers/{id}")
    public HiringManagerResponse update(@PathVariable Long id, @Valid @RequestBody HiringManagerRequest request) {
        return hiringManagerService.updateHiringManager(id, request);
    }
}
