package com.example.recruitment.service.impl;

import com.example.recruitment.dto.CreateUserRequest;
import com.example.recruitment.dto.UserResponse;
import com.example.recruitment.entity.AppUser;
import com.example.recruitment.entity.Role;
import com.example.recruitment.exception.ResourceNotFoundException;
import com.example.recruitment.repository.AppUserRepository;
import com.example.recruitment.repository.ClientRepository;
import com.example.recruitment.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final AppUserRepository users;
    private final ClientRepository clients;
    private final PasswordEncoder encoder;

    public UserServiceImpl(AppUserRepository users, ClientRepository clients, PasswordEncoder encoder) {
        this.users = users;
        this.clients = clients;
        this.encoder = encoder;
    }

    @Override
    public UserResponse createUser(CreateUserRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("A user with this email already exists");
        }
        Long clientId = null;
        if (request.role() == Role.HIRING_MANAGER || request.role() == Role.MANAGER || request.role() == Role.HR_BP) {
            if (request.clientId() == null) {
                throw new IllegalArgumentException("clientId is required for this role");
            }
            if (!clients.existsById(request.clientId())) {
                throw new ResourceNotFoundException("Client not found with id " + request.clientId());
            }
            clientId = request.clientId();
        } else if (request.role() == Role.RECRUITER && request.clientId() != null) {
            // Optional: a recruiter assigned to one client can only see that client's jobs and candidates.
            if (!clients.existsById(request.clientId())) {
                throw new ResourceNotFoundException("Client not found with id " + request.clientId());
            }
            clientId = request.clientId();
        }
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(request.password()));
        user.setRole(request.role());
        user.setClientId(clientId);
        return UserResponse.from(users.save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return users.findAllByOrderByIdAsc().stream().map(UserResponse::from).toList();
    }

    @Override
    public void deleteUser(Long id, Long currentUserId) {
        if (id.equals(currentUserId)) {
            throw new IllegalArgumentException("You cannot delete your own account");
        }
        AppUser user = users.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + id));
        users.delete(user);
    }
}
