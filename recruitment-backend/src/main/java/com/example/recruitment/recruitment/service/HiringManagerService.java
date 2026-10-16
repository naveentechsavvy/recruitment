package com.example.recruitment.service;

import com.example.recruitment.dto.HiringManagerRequest;
import com.example.recruitment.dto.HiringManagerResponse;

import java.util.List;

public interface HiringManagerService {
    HiringManagerResponse createHiringManager(Long clientId, HiringManagerRequest request);
    List<HiringManagerResponse> getHiringManagersByClient(Long clientId);
    HiringManagerResponse getHiringManager(Long id);
    HiringManagerResponse updateHiringManager(Long id, HiringManagerRequest request);
}
