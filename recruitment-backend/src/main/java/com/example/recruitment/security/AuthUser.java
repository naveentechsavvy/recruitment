package com.example.recruitment.security;

import com.example.recruitment.entity.Role;

/** The logged-in user for the current request (available with @AuthenticationPrincipal). */
public record AuthUser(Long id, String email, Role role, Long clientId) { }
