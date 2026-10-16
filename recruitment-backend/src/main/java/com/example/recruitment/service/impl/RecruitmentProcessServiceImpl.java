package com.example.recruitment.service.impl;

import com.example.recruitment.dto.RecruitmentProcessResponse;
import com.example.recruitment.repository.RecruitmentProcessRepository;
import com.example.recruitment.service.RecruitmentProcessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RecruitmentProcessServiceImpl implements RecruitmentProcessService {

    private final RecruitmentProcessRepository repository;

    public RecruitmentProcessServiceImpl(RecruitmentProcessRepository repository) { this.repository = repository; }

    @Override
    @Transactional(readOnly = true)
    public List<RecruitmentProcessResponse> getAllProcesses() {
        return repository.findAllByOrderByIdAsc().stream().map(RecruitmentProcessResponse::from).toList();
    }
}
