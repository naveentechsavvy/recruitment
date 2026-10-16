package com.example.recruitment.repository;

import com.example.recruitment.entity.RecruitmentProcess;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecruitmentProcessRepository extends JpaRepository<RecruitmentProcess, Long> {
    List<RecruitmentProcess> findAllByOrderByIdAsc();
}
