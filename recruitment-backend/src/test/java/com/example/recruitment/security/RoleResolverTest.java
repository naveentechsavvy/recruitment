
package com.example.recruitment.security;

import com.example.recruitment.entity.AppUser;
import com.example.recruitment.entity.ClientRoleAssignment;
import com.example.recruitment.entity.Role;
import com.example.recruitment.repository.ClientRoleAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoleResolverTest {

    @Mock
    ClientRoleAssignmentRepository assignments;

    RoleResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new RoleResolver(assignments);
        when(assignments.findByEmailIgnoreCaseAndClientId(anyString(), anyLong()))
                .thenReturn(Optional.empty());
    }

    private static AppUser account(String email, Role role, Long clientId) {
        AppUser u = new AppUser();
        ReflectionTestUtils.setField(u, "id", 7L);
        u.setEmail(email);
        u.setRole(role);
        u.setClientId(clientId);
        return u;
    }

    private void assigned(String email, long clientId, Role role) {
        ClientRoleAssignment a = new ClientRoleAssignment();
        a.setEmail(email);
        a.setClientId(clientId);
        a.setRole(role);

        when(assignments.findByEmailIgnoreCaseAndClientId(email, clientId))
                .thenReturn(Optional.of(a));
    }

    @Test
    void withoutAnAssignmentARegularAccountHasNoPortalAccess() {
        assertEquals(Optional.empty(),
                resolver.resolve(account("a@example.com", Role.HIRING_MANAGER, 3L), 3L));
    }

    @Test
    void assignmentAppliesOnlyToTheSelectedClient() {
        assigned("a@example.com", 9L, Role.HR_BP);

        Optional<AuthUser> result =
                resolver.resolve(account("a@example.com", Role.RECRUITER, null), 9L);

        assertEquals(Role.HR_BP, result.orElseThrow().role());
        assertEquals(9L, result.orElseThrow().clientId());
        assertEquals(7L, result.orElseThrow().id());

        assertEquals(Optional.empty(),
                resolver.resolve(account("a@example.com", Role.RECRUITER, null), 10L));
    }

    @Test
    void eachAssignableRoleIsAppliedForTheSelectedClient() {
        for (Role role : new Role[]{
                Role.HIRING_MANAGER, Role.MANAGER, Role.HR_BP, Role.RECRUITER
        }) {
            assigned("a@example.com", 9L, role);

            AuthUser result = resolver.resolve(
                    account("a@example.com", Role.RECRUITER, null), 9L
            ).orElseThrow();

            assertEquals(role, result.role());
            assertEquals(9L, result.clientId());
        }
    }

    @Test
    void systemAdminAccountStaysAdmin() {
        AuthUser result = resolver.resolve(
                account("boss@example.com", Role.ADMIN, null), 9L
        ).orElseThrow();

        assertEquals(Role.ADMIN, result.role());
        assertEquals(9L, result.clientId());
    }

    @Test
    void adminAssignmentIsScopedToTheSelectedClient() {
        assigned("a@example.com", 9L, Role.ADMIN);

        AuthUser result = resolver.resolve(
                account("a@example.com", Role.RECRUITER, null), 9L
        ).orElseThrow();

        assertEquals(Role.ADMIN, result.role());
        assertEquals(9L, result.clientId());
    }
}
