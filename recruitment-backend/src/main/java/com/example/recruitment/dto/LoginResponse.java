package com.example.recruitment.dto;

import com.example.recruitment.entity.Role;

public record LoginResponse(String token, String email, Role role, Long clientId) { }
