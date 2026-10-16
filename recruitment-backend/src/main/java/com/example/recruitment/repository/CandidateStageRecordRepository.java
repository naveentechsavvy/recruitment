package com.example.recruitment.repository;

import com.example.recruitment.entity.CandidateStageRecord;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CandidateStageRecordRepository extends JpaRepository<CandidateStageRecord, Long> {

    @EntityGraph(attributePaths = "process")
    List<CandidateStageRecord> findByCandidateId(Long candidateId);

    Optional<CandidateStageRecord> findByCandidateIdAndProcessId(Long candidateId, Long processId);
}
