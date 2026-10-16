package com.example.recruitment.repository;

import com.example.recruitment.entity.ClientProcess;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientProcessRepository extends JpaRepository<ClientProcess, Long> {

    @EntityGraph(attributePaths = "process")
    List<ClientProcess> findByClientIdOrderByDisplayOrderAsc(Long clientId);
}
