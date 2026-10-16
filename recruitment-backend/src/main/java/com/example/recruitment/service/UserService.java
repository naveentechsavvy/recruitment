package com.example.recruitment.service;

import com.example.recruitment.dto.CreateUserRequest;
import com.example.recruitment.dto.UserResponse;

import java.util.List;

public interface UserService {
    UserResponse createUser(CreateUserRequest request);
    List<UserResponse> getAllUsers();
    void deleteUser(Long id, Long currentUserId);
}
