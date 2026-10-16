package com.example.recruitment.controller;

import com.example.recruitment.dto.ClientRoleRequest;
import com.example.recruitment.dto.ClientRoleResponse;
import com.example.recruitment.dto.ClientRoleUpdateRequest;
import com.example.recruitment.security.AuthUser;
import com.example.recruitment.service.ClientRoleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Role Management of a client portal. Admin only (SecurityConfig, and again in the service). */
@RestController
@RequestMapping("/api/clients/{clientId}/roles")
public class ClientRoleController {

    private final ClientRoleService roleService;

    public ClientRoleController(ClientRoleService roleService) { this.roleService = roleService; }

    @GetMapping
    public List<ClientRoleResponse> list(@PathVariable Long clientId, @AuthenticationPrincipal AuthUser me) {
        return roleService.list(clientId, me);
    }

    @PostMapping
    public ResponseEntity<ClientRoleResponse> assign(@PathVariable Long clientId,
                                                     @Valid @RequestBody ClientRoleRequest request,
                                                     @AuthenticationPrincipal AuthUser me) {
        ClientRoleResponse created = roleService.assign(clientId, request, me);
        return ResponseEntity.created(URI.create("/api/clients/" + clientId + "/roles/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ClientRoleResponse update(@PathVariable Long clientId, @PathVariable Long id,
                                     @Valid @RequestBody ClientRoleUpdateRequest request,
                                     @AuthenticationPrincipal AuthUser me) {
        return roleService.update(clientId, id, request, me);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable Long clientId, @PathVariable Long id,
                                       @AuthenticationPrincipal AuthUser me) {
        roleService.remove(clientId, id, me);
        return ResponseEntity.noContent().build();
    }
}
