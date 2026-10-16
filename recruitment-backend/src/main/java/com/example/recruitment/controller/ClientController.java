package com.example.recruitment.controller;

import com.example.recruitment.dto.ClientProcessRequest;
import com.example.recruitment.dto.ClientProcessResponse;
import com.example.recruitment.dto.ClientRequest;
import com.example.recruitment.dto.ClientResponse;
import com.example.recruitment.security.AuthUser;
import com.example.recruitment.security.ClientAccess;
import com.example.recruitment.service.ClientService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;

/** Controllers only handle HTTP: read the request, call the service, return the response. */
@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) { this.clientService = clientService; }

    @PostMapping
    public ResponseEntity<ClientResponse> create(@Valid @RequestBody ClientRequest request) {
        ClientResponse created = clientService.createClient(request);
        return ResponseEntity.created(URI.create("/api/clients/" + created.id())).body(created); // 201
    }

    @GetMapping
    public List<ClientResponse> getAll(@AuthenticationPrincipal AuthUser me) {
        if (ClientAccess.isLimitedToOneClient(me)) {
            return List.of(clientService.getClient(me.clientId()));
        }
        return clientService.getAllClients();
    }

    @GetMapping("/{id}")
    public ClientResponse getOne(@PathVariable Long id) { return clientService.getClient(id); }

    @PutMapping("/{id}")
    public ClientResponse update(@PathVariable Long id, @Valid @RequestBody ClientRequest request) {
        return clientService.updateClient(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        clientService.deleteClient(id);
        return ResponseEntity.noContent().build(); // 204
    }

    // ----- freeze / unfreeze (admin only, see SecurityConfig) -----

    @PostMapping("/{id}/freeze")
    public ClientResponse freeze(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        return clientService.freezeClient(id, me.email());
    }

    @PostMapping("/{id}/unfreeze")
    public ClientResponse unfreeze(@PathVariable Long id) {
        return clientService.unfreezeClient(id);
    }

    // ----- logo -----

    @PostMapping(value = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ClientResponse uploadLogo(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return clientService.uploadLogo(id, file);
    }

    @DeleteMapping("/{id}/logo")
    public ResponseEntity<Void> removeLogo(@PathVariable Long id) {
        clientService.removeLogo(id);
        return ResponseEntity.noContent().build();
    }

    // ----- client process configuration -----

    @PostMapping("/{clientId}/processes")
    public List<ClientProcessResponse> configureProcesses(@PathVariable Long clientId,
                                                          @Valid @RequestBody ClientProcessRequest request) {
        return clientService.configureProcesses(clientId, request);
    }

    @GetMapping("/{clientId}/processes")
    public List<ClientProcessResponse> getProcesses(@PathVariable Long clientId) {
        return clientService.getClientProcesses(clientId);
    }
}
