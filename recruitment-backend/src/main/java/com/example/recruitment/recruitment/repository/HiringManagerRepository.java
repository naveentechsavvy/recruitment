package com.example.recruitment.repository;

import com.example.recruitment.entity.HiringManager;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HiringManagerRepository extends JpaRepository<HiringManager, Long> {

    List<HiringManager> findByClientIdOrderByCreatedAtDesc(Long clientId);

    /** Used to make sure a hiring manager really belongs to the client in the URL. */
    java.util.Optional<HiringManager> findByIdAndClientId(Long id, Long clientId);

    boolean existsByClientIdAndEmail(Long clientId, String email);

    boolean existsByClientIdAndEmailAndIdNot(Long clientId, String email, Long id);
}
