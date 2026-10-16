package com.example.recruitment.controller;

import com.example.recruitment.dto.PublicClientResponse;
import com.example.recruitment.dto.PublicJobResponse;
import com.example.recruitment.service.PublicClientService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public, read-only endpoints for the client application page at /app/{slug}. */
@RestController
@RequestMapping("/api/public/clients")
public class PublicClientController {

    private final PublicClientService publicClientService;

    public PublicClientController(PublicClientService publicClientService) {
        this.publicClientService = publicClientService;
    }

    @GetMapping("/{slug}")
    public PublicClientResponse getApplication(@PathVariable String slug) {
        return publicClientService.getApplication(slug);
    }

    @GetMapping("/{slug}/jobs")
    public List<PublicJobResponse> getOpenJobs(@PathVariable String slug) {
        return publicClientService.getOpenJobs(slug);
    }
}
