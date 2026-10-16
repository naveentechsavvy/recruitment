package com.example.recruitment.dto;

import com.example.recruitment.entity.RecruitmentProcess;

public record RecruitmentProcessResponse(Long id, String code, String name, String description) {
    public static RecruitmentProcessResponse from(RecruitmentProcess p) {
        return new RecruitmentProcessResponse(p.getId(), p.getCode(), p.getName(), p.getDescription());
    }
}
