package com.example.recruitment.repository;

import com.example.recruitment.entity.PreonboardingCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PreonboardingCaseRepository extends JpaRepository<PreonboardingCase, Long> {

    Optional<PreonboardingCase> findByCandidateId(Long candidateId);

    /** Used only when a candidate is deleted. */
    void deleteByCandidateId(Long candidateId);
}
