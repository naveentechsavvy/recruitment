package com.example.recruitment.repository;
import com.example.recruitment.entity.PreonboardingDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface PreonboardingDocumentRepository extends JpaRepository<PreonboardingDocument,Long> {
 List<PreonboardingDocument> findByPreonboardingCaseIdOrderByUploadedAtAsc(Long caseId);
 Optional<PreonboardingDocument> findByIdAndPreonboardingCaseId(Long id, Long caseId);
 void deleteByPreonboardingCaseId(Long caseId);
}
