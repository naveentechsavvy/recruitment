
package com.example.recruitment.security;

import com.example.recruitment.entity.AppUser;
import com.example.recruitment.entity.Role;
import com.example.recruitment.repository.ClientRoleAssignmentRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class RoleResolver {

    private final ClientRoleAssignmentRepository assignments;

    public RoleResolver(ClientRoleAssignmentRepository assignments) {
        this.assignments = assignments;
    }

    public Optional<AuthUser> resolve(AppUser user, Long selectedClientId) {

        // System administrators retain their system-wide ADMIN role.
        if (user.getRole() == Role.ADMIN) {
            return Optional.of(
                    new AuthUser(user.getId(), user.getEmail(), Role.ADMIN, selectedClientId)
            );
        }

        // A non-admin must have an assignment for the selected client.
        if (selectedClientId == null || user.getEmail() == null) {
            return Optional.empty();
        }

        return assignments
                .findByEmailIgnoreCaseAndClientId(
                        user.getEmail(), selectedClientId)
                .map(a -> new AuthUser(
                        user.getId(),
                        user.getEmail(),
                        a.getRole(),
                        a.getClientId()
                ));
    }
}
