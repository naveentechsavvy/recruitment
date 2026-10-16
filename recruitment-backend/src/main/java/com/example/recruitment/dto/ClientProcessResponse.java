package com.example.recruitment.dto;

import com.example.recruitment.entity.ClientProcess;

public record ClientProcessResponse(Long processId, String code, String name, boolean enabled, int displayOrder) {
    public static ClientProcessResponse from(ClientProcess cp) {
        return new ClientProcessResponse(cp.getProcess().getId(), cp.getProcess().getCode(),
                cp.getProcess().getName(), cp.isEnabled(), cp.getDisplayOrder());
    }
}
