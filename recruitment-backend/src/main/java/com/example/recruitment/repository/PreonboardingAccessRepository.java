package com.example.recruitment.repository;
import com.example.recruitment.entity.PreonboardingAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface PreonboardingAccessRepository extends JpaRepository<PreonboardingAccess,Long> {
 Optional<PreonboardingAccess> findByTokenHash(String tokenHash);
 Optional<PreonboardingAccess> findByPreonboardingCaseId(Long caseId);
 void deleteByPreonboardingCaseId(Long caseId);
}
