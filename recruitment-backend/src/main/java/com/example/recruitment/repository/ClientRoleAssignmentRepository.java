
package com.example.recruitment.repository;

import com.example.recruitment.entity.ClientRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClientRoleAssignmentRepository extends JpaRepository<ClientRoleAssignment, Long> {

    List<ClientRoleAssignment> findByClientIdOrderByEmailAsc(Long clientId);

    List<ClientRoleAssignment> findAllByEmailIgnoreCase(String email);

    Optional<ClientRoleAssignment> findByEmailIgnoreCaseAndClientId(
            String email, Long clientId);

    Optional<ClientRoleAssignment> findByIdAndClientId(Long id, Long clientId);
}
