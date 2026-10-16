package com.example.recruitment.repository;

import com.example.recruitment.entity.Requirement;
import com.example.recruitment.entity.RequirementStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RequirementRepository extends JpaRepository<Requirement, Long> {

    /** Only this client's requirements, with the hiring manager loaded in the same query. */
    @EntityGraph(attributePaths = "hiringManager")
    List<Requirement> findByClientIdOrderByCreatedAtDesc(Long clientId);

    /** Public page: only the given client's requirements with the given status (Open). */
    List<Requirement> findByClientIdAndStatusOrderByCreatedAtDesc(Long clientId, RequirementStatus status);

    boolean existsByClientIdAndJobId(Long clientId, String jobId);

    /** Used when a candidate applies: the job id in the URL must belong to the client in the URL. */
    Optional<Requirement> findByClientIdAndJobId(Long clientId, String jobId);

    boolean existsByClientIdAndJobIdAndIdNot(Long clientId, String jobId, Long id);
}
