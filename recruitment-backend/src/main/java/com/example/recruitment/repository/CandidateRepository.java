package com.example.recruitment.repository;

import com.example.recruitment.entity.Candidate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {

    @EntityGraph(attributePaths = "currentProcess")
    List<Candidate> findByRequirementIdOrderByCreatedAtDesc(Long requirementId);

    boolean existsByRequirementIdAndEmailIgnoreCase(Long requirementId, String email);
}
