package com.example.recruitment.repository;

import com.example.recruitment.entity.CandidateEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CandidateEventRepository extends JpaRepository<CandidateEvent, Long> {

    /** Oldest first, so the timeline reads in the order things happened. */
    List<CandidateEvent> findByCandidateIdOrderByCreatedAtAscIdAsc(Long candidateId);
}
