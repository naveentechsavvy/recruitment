package com.example.recruitment.repository;

import com.example.recruitment.entity.PreonboardingCase;
import com.example.recruitment.entity.PreonboardingStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PreonboardingCaseRepository extends JpaRepository<PreonboardingCase, Long> {

    Optional<PreonboardingCase> findByCandidateId(Long candidateId);

    List<PreonboardingCase> findByClientId(Long clientId);

    @EntityGraph(attributePaths = {"candidate", "candidate.requirement"})
    List<PreonboardingCase> findByClientIdAndStatusOrderByCreatedAtDesc(
            Long clientId,
            PreonboardingStatus status
    );

    /** Used only when a candidate is deleted. */
    void deleteByCandidateId(Long candidateId);
}
