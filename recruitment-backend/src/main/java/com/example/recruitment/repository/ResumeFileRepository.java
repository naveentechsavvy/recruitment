package com.example.recruitment.repository;

import com.example.recruitment.entity.ResumeFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ResumeFileRepository extends JpaRepository<ResumeFile, Long> {

    Optional<ResumeFile> findByFilename(String filename);

    boolean existsByFilename(String filename);

    /** Returns how many rows were removed (0 when the file was already gone, which is fine). */
    @Modifying
    @Query("delete from ResumeFile r where r.filename = :filename")
    int deleteByFilename(@Param("filename") String filename);
}
