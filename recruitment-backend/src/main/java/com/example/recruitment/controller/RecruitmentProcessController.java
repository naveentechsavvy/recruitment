package com.example.recruitment.controller;

import com.example.recruitment.dto.RecruitmentProcessResponse;
import com.example.recruitment.service.RecruitmentProcessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recruitment-processes")
public class RecruitmentProcessController {

    private final RecruitmentProcessService service;

    public RecruitmentProcessController(RecruitmentProcessService service) { this.service = service; }

    @GetMapping
    public List<RecruitmentProcessResponse> getAll() { return service.getAllProcesses(); }
}
