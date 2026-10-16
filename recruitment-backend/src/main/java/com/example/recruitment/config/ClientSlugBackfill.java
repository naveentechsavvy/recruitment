package com.example.recruitment.config;

import com.example.recruitment.entity.Client;
import com.example.recruitment.repository.ClientRepository;
import com.example.recruitment.service.ClientSlugGenerator;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * On start, gives a slug to every client that was created before slugs existed (e.g. Samsung).
 * Hibernate (ddl-auto=update) adds the empty slug column first; this fills it. Safe to run every time.
 */
@Component
public class ClientSlugBackfill implements CommandLineRunner {

    private final ClientRepository clientRepository;
    private final ClientSlugGenerator slugGenerator;

    public ClientSlugBackfill(ClientRepository clientRepository, ClientSlugGenerator slugGenerator) {
        this.clientRepository = clientRepository;
        this.slugGenerator = slugGenerator;
    }

    @Override
    public void run(String... args) {
        for (Client client : clientRepository.findBySlugIsNull()) {
            client.setSlug(slugGenerator.uniqueSlug(client.getName()));
            clientRepository.save(client); // saved one by one so the next client sees this slug as taken
        }
    }
}
