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
import com.example.recruitment.service.InvitationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientRoleServiceImplTest {

    @Mock ClientRoleAssignmentRepository assignments;
    @Mock ClientRepository clients;
    @Mock AppUserRepository users;
    @Mock InvitationService invitations;

    final AuthUser admin = new AuthUser(1L, "admin@example.com", Role.ADMIN, null);
    final AuthUser recruiter = new AuthUser(2L, "rec@example.com", Role.RECRUITER, null);
    final AuthUser manager = new AuthUser(3L, "mgr@example.com", Role.MANAGER, 4L);

    final List<ClientRoleAssignment> stored = new ArrayList<>();
    ClientRoleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClientRoleServiceImpl(assignments, clients, users, invitations);
        when(clients.existsById(4L)).thenReturn(true);
        when(clients.existsById(99L)).thenReturn(false);
        when(assignments.findAllByEmailIgnoreCase(anyString()))
        .thenReturn(List.of());
        when(assignments.findByEmailIgnoreCaseAndClientId(anyString(), anyLong()))
        .thenReturn(Optional.empty());
        when(assignments.save(any(ClientRoleAssignment.class))).thenAnswer(inv -> {
            ClientRoleAssignment a = inv.getArgument(0);
            if (a.getId() == null) ReflectionTestUtils.setField(a, "id", 100L + stored.size());
            stored.add(a);
            return a;
        });
    }

    private ClientRoleAssignment existing(
        long id, long clientId, String email, Role role) {

    ClientRoleAssignment a = new ClientRoleAssignment();
    ReflectionTestUtils.setField(a, "id", id);
    a.setClientId(clientId);
    a.setEmail(email);
    a.setRole(role);
    a.setAssignedBy("admin@example.com");

    when(assignments.findByIdAndClientId(id, clientId))
            .thenReturn(Optional.of(a));

    when(assignments.findByEmailIgnoreCaseAndClientId(email, clientId))
            .thenReturn(Optional.of(a));

    when(assignments.findAllByEmailIgnoreCase(email))
            .thenReturn(List.of(a));

    return a;
}
    // ---------------------------------------------------------------- assigning

    @Test
    void adminCanAssignEachOfTheFourClientRoles() {
        int n = 0;
        for (Role role : List.of(Role.HIRING_MANAGER, Role.MANAGER, Role.HR_BP, Role.RECRUITER)) {
            ClientRoleResponse r = service.assign(4L, new ClientRoleRequest("person" + (n++) + "@example.com", role), admin);
            assertEquals(role, r.role());
            assertEquals(4L, r.clientId());
        }
        assertEquals(4, stored.size());
    }

    @Test
    void adminRoleCanNeverBeGivenAsAClientRole() {
        existing(10L, 4L, "p@example.com", Role.RECRUITER);

        assertThrows(IllegalArgumentException.class,
                () -> service.assign(4L, new ClientRoleRequest("new@example.com", Role.ADMIN), admin));
        assertThrows(IllegalArgumentException.class,
                () -> service.update(4L, 10L, new ClientRoleUpdateRequest(Role.ADMIN), admin));
        verify(assignments, never()).save(any());
        verify(invitations, never()).processAssignment(any());
    }

    @Test
    void emailIsTrimmedAndStoredInLowerCaseWithTheAssigningAdmin() {
        ClientRoleResponse r = service.assign(4L, new ClientRoleRequest("  Jane.Doe@Example.COM ", Role.RECRUITER), admin);

        assertEquals("jane.doe@example.com", r.email());
        assertEquals("admin@example.com", r.assignedBy());
    }

    @Test
    void responseTellsWhetherThePersonAlreadyHasALogin() {
        AppUser active = new AppUser();
        active.setEmail("has@example.com");
        active.setRole(Role.MANAGER);
        active.setActive(true);
        when(users.findByEmailIgnoreCase("has@example.com")).thenReturn(Optional.of(active));

        assertTrue(service.assign(4L, new ClientRoleRequest("has@example.com", Role.MANAGER), admin).hasAccount());
        assertFalse(service.assign(4L, new ClientRoleRequest("new@example.com", Role.MANAGER), admin).hasAccount());
    }

    @Test
    void sameEmailCannotGetASecondRoleOnThisPortal() {
        existing(10L, 4L, "dup@example.com", Role.RECRUITER);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.assign(4L, new ClientRoleRequest("DUP@example.com", Role.MANAGER), admin));
        assertTrue(e.getMessage().contains("this portal"));
        verify(assignments, never()).save(any());
    }

    @Test
    void sameEmailCanHaveADifferentRoleOnAnotherPortal() {
        existing(10L, 7L, "dup@example.com", Role.RECRUITER); // already on portal 7

        ClientRoleResponse r = service.assign(4L, new ClientRoleRequest("dup@example.com", Role.HR_BP), admin);

        assertEquals(4L, r.clientId());
        assertEquals(Role.HR_BP, r.role());
        verify(assignments).save(any(ClientRoleAssignment.class));
        verify(invitations).processAssignment(any(ClientRoleAssignment.class)); // the "access granted" email, no new password
    }

    @Test
    void theSameEmailStillCannotGetTwoRolesOnOnePortal() {
        existing(10L, 4L, "dup@example.com", Role.RECRUITER);
        existing(11L, 7L, "dup@example.com", Role.MANAGER);

        assertThrows(IllegalArgumentException.class,
                () -> service.assign(4L, new ClientRoleRequest("dup@example.com", Role.HR_BP), admin));
        verify(assignments, never()).save(any());
    }

    @Test
    void removingOnePortalKeepsTheLoginWhileAnotherPortalRemains() {
        ClientRoleAssignment onFour = existing(10L, 4L, "p@example.com", Role.MANAGER);
        ClientRoleAssignment onSeven = existing(11L, 7L, "p@example.com", Role.RECRUITER);
        when(assignments.findAllByEmailIgnoreCase("p@example.com")).thenReturn(List.of(onSeven));

        service.remove(4L, 10L, admin);

        verify(assignments).delete(onFour);
        verify(invitations, never()).revokeAccess(anyString());
    }

    @Test
    void removingTheLastPortalSwitchesTheLoginOff() {
        existing(10L, 4L, "p@example.com", Role.MANAGER);
        when(assignments.findAllByEmailIgnoreCase("p@example.com")).thenReturn(List.of());

        service.remove(4L, 10L, admin);

        verify(invitations).revokeAccess("p@example.com");
    }

    @Test
    void systemAdminAccountCannotBeGivenAPortalRole() {
        AppUser sysAdmin = new AppUser();
        sysAdmin.setEmail("boss@example.com");
        sysAdmin.setRole(Role.ADMIN);
        when(users.findByEmailIgnoreCase("boss@example.com")).thenReturn(Optional.of(sysAdmin));

        assertThrows(IllegalArgumentException.class,
                () -> service.assign(4L, new ClientRoleRequest("boss@example.com", Role.RECRUITER), admin));
    }

    @Test
    void adminCannotChangeTheirOwnRole() {
        assertThrows(IllegalArgumentException.class,
                () -> service.assign(4L, new ClientRoleRequest("ADMIN@example.com", Role.RECRUITER), admin));
    }

    @Test
    void assigningOnAMissingPortalGivesNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> service.assign(99L, new ClientRoleRequest("a@example.com", Role.RECRUITER), admin));
    }

    // ---------------------------------------------------------------- updating and removing

    @Test
    void roleCanBeUpdated() {
        ClientRoleAssignment a = existing(10L, 4L, "p@example.com", Role.RECRUITER);

        ClientRoleResponse r = service.update(4L, 10L, new ClientRoleUpdateRequest(Role.HR_BP), admin);

        assertEquals(Role.HR_BP, r.role());
        assertEquals(Role.HR_BP, a.getRole());
        assertEquals("p@example.com", r.email()); // the email never changes
    }

    @Test
    void anAssignmentOfAnotherPortalCannotBeTouchedThroughThisPortal() {
        when(assignments.findByIdAndClientId(10L, 4L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.update(4L, 10L, new ClientRoleUpdateRequest(Role.MANAGER), admin));
        assertThrows(ResourceNotFoundException.class, () -> service.remove(4L, 10L, admin));
    }

    @Test
    void roleCanBeRemoved() {
        ClientRoleAssignment a = existing(10L, 4L, "p@example.com", Role.MANAGER);

        service.remove(4L, 10L, admin);

        verify(assignments).delete(a);
    }

    @Test
    void ownAssignmentCannotBeChangedOrRemoved() {
        // an admin who was given a portal role through Role Management cannot edit or delete their own row
        existing(11L, 4L, "admin@example.com", Role.ADMIN);

        assertThrows(IllegalArgumentException.class, () -> service.update(4L, 11L, new ClientRoleUpdateRequest(Role.RECRUITER), admin));
        assertThrows(IllegalArgumentException.class, () -> service.remove(4L, 11L, admin));
    }

    // ---------------------------------------------------------------- authorization

    @Test
    void onlyAnAdminCanReadOrChangeRoles() {
        existing(10L, 4L, "p@example.com", Role.RECRUITER);

        for (AuthUser notAdmin : List.of(recruiter, manager)) {
            assertThrows(AccessDeniedException.class, () -> service.list(4L, notAdmin));
            assertThrows(AccessDeniedException.class,
                    () -> service.assign(4L, new ClientRoleRequest("x@example.com", Role.RECRUITER), notAdmin));
            assertThrows(AccessDeniedException.class, () -> service.update(4L, 10L, new ClientRoleUpdateRequest(Role.ADMIN), notAdmin));
            assertThrows(AccessDeniedException.class, () -> service.remove(4L, 10L, notAdmin));
        }
        assertThrows(AccessDeniedException.class, () -> service.list(4L, null));
        verify(assignments, never()).save(any());
        verify(assignments, never()).delete(any());
    }

    @Test
    void listReturnsTheAssignmentsOfThePortal() {
        ClientRoleAssignment first = existing(10L, 4L, "a@example.com", Role.RECRUITER);
        ClientRoleAssignment second = existing(11L, 4L, "b@example.com", Role.MANAGER);
        when(assignments.findByClientIdOrderByEmailAsc(4L)).thenReturn(List.of(first, second));

        List<ClientRoleResponse> list = service.list(4L, admin);

        assertEquals(2, list.size());
        assertEquals("a@example.com", list.get(0).email());
        assertEquals(Role.MANAGER, list.get(1).role());
    }
}
