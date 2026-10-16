
package com.example.recruitment.repository;

import com.example.recruitment.entity.Client;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {

    /** Loads clients with their processes in one query. */
    @EntityGraph(attributePaths = {"clientProcesses", "clientProcesses.process"})
    List<Client> findAllByOrderByCreatedAtDesc();

    /** Used by the public application: /app/{slug}. */
    @EntityGraph(attributePaths = {"clientProcesses", "clientProcesses.process"})
    Optional<Client> findBySlug(String slug);

    /** Finds a client portal without case sensitivity. */
    Optional<Client> findBySlugIgnoreCase(String slug);

    boolean existsBySlug(String slug);

    /** Clients created before slugs existed. */
    List<Client> findBySlugIsNull();
}
