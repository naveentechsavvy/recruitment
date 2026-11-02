package com.example.recruitment.repository;

import com.example.recruitment.entity.PreonboardingConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PreonboardingConfigRepository extends JpaRepository<PreonboardingConfig, Long> {

    Optional<PreonboardingConfig> findByClientId(Long clientId);
}
