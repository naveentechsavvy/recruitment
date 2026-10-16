package com.example.recruitment.service;

import com.example.recruitment.dto.PublicClientResponse;
import com.example.recruitment.dto.PublicJobResponse;

import java.util.List;

/** Read-only data for the public client application (/app/{slug}). */
public interface PublicClientService {
    PublicClientResponse getApplication(String slug);
    List<PublicJobResponse> getOpenJobs(String slug);
}
