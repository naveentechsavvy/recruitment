package com.example.recruitment.service;

import com.example.recruitment.dto.CandidateResponse;
import com.example.recruitment.dto.RemarkRequest;
import com.example.recruitment.dto.ScreeningActionRequest;
import com.example.recruitment.dto.ScreeningUpdateRequest;
import com.example.recruitment.dto.StageUpdateRequest;
import com.example.recruitment.security.AuthUser;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CandidateService {

    void apply(String slug, String jobId, String fullName, String email, String phone, MultipartFile resume);

    CandidateResponse addCandidate(
            Long requirementId,
            String fullName,
            String email,
            String phone,
            MultipartFile resume,
            AuthUser user);

    List<CandidateResponse> getByRequirement(Long requirementId, AuthUser user);

    CandidateResponse getCandidate(Long id, AuthUser user);

    CandidateResponse updateScreening(Long id, ScreeningUpdateRequest request, AuthUser user);

    CandidateResponse screeningAction(Long id, ScreeningActionRequest request, AuthUser user);

    CandidateResponse addRemark(Long id, RemarkRequest request, AuthUser user);

    CandidateResponse updateStage(Long id, String stageCode, StageUpdateRequest request, AuthUser user);

    ResumeStorageService.Loaded getResume(Long id, AuthUser user);

    CandidateResponse replaceResume(Long id, MultipartFile resume, AuthUser user);
}
