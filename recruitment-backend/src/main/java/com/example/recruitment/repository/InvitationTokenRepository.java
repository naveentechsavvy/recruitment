package com.example.recruitment.repository;

import com.example.recruitment.entity.InvitationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InvitationTokenRepository extends JpaRepository<InvitationToken, Long> {
    Optional<InvitationToken> findByTokenHash(String tokenHash);
    void deleteByEmailIgnoreCase(String email);
}
