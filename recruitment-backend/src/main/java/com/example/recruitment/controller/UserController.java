package com.example.recruitment.controller;

import com.example.recruitment.dto.CreateUserRequest;
import com.example.recruitment.dto.UserResponse;
import com.example.recruitment.security.AuthUser;
import com.example.recruitment.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Admin-only (enforced in SecurityConfig): create and manage recruiter / hiring manager logins. */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.createUser(request);
        return ResponseEntity.created(URI.create("/api/users/" + created.id())).body(created);
    }

    @GetMapping
    public List<UserResponse> getAll() {
        return userService.getAllUsers();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        userService.deleteUser(id, me.id());
        return ResponseEntity.noContent().build();
    }
}
