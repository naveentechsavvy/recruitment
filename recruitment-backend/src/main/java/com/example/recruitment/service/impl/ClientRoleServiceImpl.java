package com.example.recruitment.service.impl;

import com.example.recruitment.dto.ClientRoleRequest;
import com.example.recruitment.dto.ClientRoleResponse;
import com.example.recruitment.dto.ClientRoleUpdateRequest;
import com.example.recruitment.entity.AppUser;
import com.example.recruitment.entity.ClientRoleAssignment;
import com.example.recruitment.entity.Role;
import com.example.recruitment.exception.ResourceNotFoundException;
import com.example.recruitment.repository.AppUserRepository;
import com.example.recruitment.repository.ClientRepository;
import com.example.recruitment.repository.ClientRoleAssignmentRepository;
import com.example.recruitment.security.AuthUser;
import com.example.recruitment.service.ClientRoleService;
import com.example.recruitment.service.InvitationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Rules:
 *  - Only an ADMIN may read or change roles.
 *  - The same email can have a role on SEVERAL client portals, a different one on each.
 *  - Only one role per email on one portal.
 *  - A system admin account cannot be given a portal role.
 *  - Nobody can change or remove their own assignment.
 *  - Assigning a role sends an invitation or an access-granted notification.
 *  - Removing a role deactivates the login when no assignments remain.
 *  - A frozen portal can still have its roles managed.
 */
@Service
@Transactional
public class ClientRoleServiceImpl implements ClientRoleService {

    private final ClientRoleAssignmentRepository assignments;
    private final ClientRepository clients;
    private final AppUserRepository users;
    private final InvitationService invitations;

    public ClientRoleServiceImpl(
            ClientRoleAssignmentRepository assignments,
            ClientRepository clients,
            AppUserRepository users,
            InvitationService invitations) {
        this.assignments = assignments;
        this.clients = clients;
        this.users = users;
        this.invitations = invitations;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClientRoleResponse> list(Long clientId, AuthUser actor) {
        requireAdmin(actor);
        requireClient(clientId);

        return assignments.findByClientIdOrderByEmailAsc(clientId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public ClientRoleResponse assign(
            Long clientId,
            ClientRoleRequest request,
            AuthUser actor) {

        requireAdmin(actor);
        rejectAdminRole(request.role());
        requireClient(clientId);

        String email = normalize(request.email());

        if (email.equalsIgnoreCase(actor.email())) {
            throw new IllegalArgumentException(
                    "You cannot change your own role");
        }

        Optional<AppUser> account = users.findByEmailIgnoreCase(email);

        if (account.isPresent() && account.get().getRole() == Role.ADMIN) {
            throw new IllegalArgumentException(
                    "This email belongs to a system administrator "
                            + "and cannot be given a portal role");
        }

        // Prevent duplicate assignments on the same portal.
        if (assignments.findByEmailIgnoreCaseAndClientId(email, clientId)
                .isPresent()) {
            throw new IllegalArgumentException(
                    "This email already has a role on this portal. "
                            + "Update that role instead");
        }

        ClientRoleAssignment assignment = new ClientRoleAssignment();
        assignment.setClientId(clientId);
        assignment.setEmail(email);
        assignment.setRole(request.role());
        assignment.setAssignedBy(actor.email());

        ClientRoleAssignment saved = assignments.save(assignment);

        // Sends an invitation or access-granted email.
        // An email failure rolls back the assignment.
        invitations.processAssignment(saved);

        return toResponse(saved);
    }

    @Override
    public ClientRoleResponse update(
            Long clientId,
            Long assignmentId,
            ClientRoleUpdateRequest request,
            AuthUser actor) {

        requireAdmin(actor);

        ClientRoleAssignment assignment = find(clientId, assignmentId);

        if (assignment.getEmail().equalsIgnoreCase(actor.email())) {
            throw new IllegalArgumentException(
                    "You cannot change your own role");
        }

        rejectAdminRole(request.role());

        if (assignment.getRole() != request.role()) {
            assignment.setRole(request.role());
            assignment.setAssignedBy(actor.email());
        }

        return toResponse(assignment);
    }

    @Override
    public void remove(
            Long clientId,
            Long assignmentId,
            AuthUser actor) {

        requireAdmin(actor);

        ClientRoleAssignment assignment = find(clientId, assignmentId);

        if (assignment.getEmail().equalsIgnoreCase(actor.email())) {
            throw new IllegalArgumentException(
                    "You cannot remove your own role");
        }

        assignments.delete(assignment);
        assignments.flush();

        // Revoke access only when no assignments remain.
        if (assignments.findAllByEmailIgnoreCase(
                assignment.getEmail()).isEmpty()) {
            invitations.revokeAccess(assignment.getEmail());
        }
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private static void requireAdmin(AuthUser actor) {
        if (actor == null || actor.role() != Role.ADMIN) {
            throw new AccessDeniedException(
                    "Only an admin can manage roles");
        }
    }

    private void requireClient(Long clientId) {
        if (!clients.existsById(clientId)) {
            throw new ResourceNotFoundException(
                    "Client not found with id " + clientId);
        }
    }

    /** A client invitation must never create a System Admin. */
    private static void rejectAdminRole(Role role) {
        if (role == Role.ADMIN) {
            throw new IllegalArgumentException(
                    "ADMIN cannot be given as a client role. System administrators are created separately");
        }
    }

    private ClientRoleAssignment find(
            Long clientId,
            Long assignmentId) {

        requireClient(clientId);

        return assignments.findByIdAndClientId(assignmentId, clientId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Role assignment not found"));
    }

    private ClientRoleResponse toResponse(ClientRoleAssignment assignment) {
        return ClientRoleResponse.from(
                assignment,
                users.findByEmailIgnoreCase(assignment.getEmail())
                        .map(AppUser::isActive)
                        .orElse(false));
    }

    private static String normalize(String email) {
        return email == null
                ? ""
                : email.trim().toLowerCase(Locale.ROOT);
    }
}