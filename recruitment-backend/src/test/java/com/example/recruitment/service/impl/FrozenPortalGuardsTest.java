package com.example.recruitment.service.impl;

import com.example.recruitment.entity.Client;
import com.example.recruitment.entity.HiringManager;
import com.example.recruitment.entity.Requirement;
import com.example.recruitment.entity.RequirementStatus;
import com.example.recruitment.exception.PortalFrozenException;
import com.example.recruitment.repository.ClientRepository;
import com.example.recruitment.repository.HiringManagerRepository;
import com.example.recruitment.repository.RequirementRepository;
import com.example.recruitment.service.PortalGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A frozen portal refuses new jobs, job changes and hiring-manager changes on the server.
 * The request body is never read: the freeze check happens first, so these tests pass null.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FrozenPortalGuardsTest {

    @Mock RequirementRepository requirementRepository;
    @Mock HiringManagerRepository hiringManagerRepository;
    @Mock ClientRepository clientRepository;

    Client client;
    Requirement requirement;
    HiringManager manager;

    @BeforeEach
    void setUp() {
        client = new Client();
        ReflectionTestUtils.setField(client, "id", 4L);
        client.setFrozen(true);

        requirement = new Requirement();
        ReflectionTestUtils.setField(requirement, "id", 10L);
        requirement.setClient(client);
        requirement.setStatus(RequirementStatus.OPEN);

        manager = new HiringManager();
        ReflectionTestUtils.setField(manager, "id", 20L);
        manager.setClient(client);

        when(clientRepository.findById(4L)).thenReturn(Optional.of(client));
        when(requirementRepository.findById(10L)).thenReturn(Optional.of(requirement));
        when(hiringManagerRepository.findById(20L)).thenReturn(Optional.of(manager));
    }

    @Test
    void guardRefusesAFrozenPortalAndAllowsAnActiveOne() {
        PortalFrozenException e = assertThrows(PortalFrozenException.class, () -> PortalGuard.requireActive(client));
        assertTrue(e.getMessage().contains("frozen"));
        client.setFrozen(false);
        assertDoesNotThrow(() -> PortalGuard.requireActive(client));
    }

    @Test
    void aFrozenPortalTakesNoNewJobs() {
        RequirementServiceImpl service = new RequirementServiceImpl(requirementRepository, hiringManagerRepository, clientRepository);
        assertThrows(PortalFrozenException.class, () -> service.createRequirement(4L, null));
        verify(requirementRepository, never()).save(any());
    }

    @Test
    void aFrozenPortalCannotChangeOrCloseAJob() {
        RequirementServiceImpl service = new RequirementServiceImpl(requirementRepository, hiringManagerRepository, clientRepository);
        assertThrows(PortalFrozenException.class, () -> service.updateRequirement(10L, null));
        assertThrows(PortalFrozenException.class, () -> service.updateStatus(10L, RequirementStatus.CLOSED));
        assertEquals(RequirementStatus.OPEN, requirement.getStatus());
    }

    @Test
    void aFrozenPortalCannotAddOrChangeHiringManagers() {
        HiringManagerServiceImpl service = new HiringManagerServiceImpl(hiringManagerRepository, clientRepository);
        assertThrows(PortalFrozenException.class, () -> service.createHiringManager(4L, null));
        assertThrows(PortalFrozenException.class, () -> service.updateHiringManager(20L, null));
        verify(hiringManagerRepository, never()).save(any());
    }

    @Test
    void aFrozenPortalShowsNoOpenJobsToThePublic() {
        client.setSlug("acme");
        client.setStatus(com.example.recruitment.entity.ClientStatus.ACTIVE);
        when(clientRepository.findBySlug("acme")).thenReturn(Optional.of(client));
        PublicClientServiceImpl service = new PublicClientServiceImpl(clientRepository, requirementRepository);

        assertTrue(service.getOpenJobs("acme").isEmpty());
        assertTrue(service.getApplication("acme").frozen()); // the page still loads and says it is frozen
        verify(requirementRepository, never()).findByClientIdAndStatusOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void anUnfrozenPortalShowsItsOpenJobs() {
        client.setFrozen(false);
        client.setSlug("acme");
        client.setStatus(com.example.recruitment.entity.ClientStatus.ACTIVE);
        when(clientRepository.findBySlug("acme")).thenReturn(Optional.of(client));
        when(requirementRepository.findByClientIdAndStatusOrderByCreatedAtDesc(4L, RequirementStatus.OPEN)).thenReturn(List.of());
        PublicClientServiceImpl service = new PublicClientServiceImpl(clientRepository, requirementRepository);

        assertTrue(service.getOpenJobs("acme").isEmpty());
        verify(requirementRepository).findByClientIdAndStatusOrderByCreatedAtDesc(4L, RequirementStatus.OPEN);
    }
}
