package com.example.recruitment.repository;

import com.example.recruitment.entity.PreonboardingSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PreonboardingSettingRepository extends JpaRepository<PreonboardingSetting, Long> {

    List<PreonboardingSetting> findByClientId(Long clientId);
}
