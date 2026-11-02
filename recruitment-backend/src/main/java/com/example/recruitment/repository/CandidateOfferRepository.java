package com.example.recruitment.repository;

import com.example.recruitment.entity.CandidateOffer;
import com.example.recruitment.entity.OfferStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CandidateOfferRepository extends JpaRepository<CandidateOffer, Long> {

    @EntityGraph(attributePaths = {"candidate", "candidate.client", "candidate.requirement"})
    Optional<CandidateOffer> findByTokenHash(String tokenHash);

    Optional<CandidateOffer> findFirstByCandidateIdOrderByIdDesc(Long candidateId);

    List<CandidateOffer> findByCandidateIdAndStatus(Long candidateId, OfferStatus status);

    /** Used only when a candidate is deleted. */
    void deleteByCandidateId(Long candidateId);
}
