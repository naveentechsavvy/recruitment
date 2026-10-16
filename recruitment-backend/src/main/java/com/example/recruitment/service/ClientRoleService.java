package com.example.recruitment.service;

import com.example.recruitment.dto.ClientRoleRequest;
import com.example.recruitment.dto.ClientRoleResponse;
import com.example.recruitment.dto.ClientRoleUpdateRequest;
import com.example.recruitment.security.AuthUser;

import java.util.List;

/** Role Management of one client portal: which email has which role. Admin only. */
public interface ClientRoleService {
    List<ClientRoleResponse> list(Long clientId, AuthUser actor);
    ClientRoleResponse assign(Long clientId, ClientRoleRequest request, AuthUser actor);
    ClientRoleResponse update(Long clientId, Long assignmentId, ClientRoleUpdateRequest request, AuthUser actor);
    void remove(Long clientId, Long assignmentId, AuthUser actor);
}
