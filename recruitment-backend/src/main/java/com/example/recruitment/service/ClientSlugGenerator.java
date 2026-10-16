package com.example.recruitment.service;

import com.example.recruitment.repository.ClientRepository;
import com.example.recruitment.util.SlugUtil;
import org.springframework.stereotype.Component;

/** Builds a slug that no other client uses yet: "apple", then "apple-2", "apple-3", ... */
@Component
public class ClientSlugGenerator {

    private final ClientRepository clientRepository;

    public ClientSlugGenerator(ClientRepository clientRepository) { this.clientRepository = clientRepository; }

    public String uniqueSlug(String name) {
        String base = SlugUtil.slugify(name);
        String slug = base;
        int next = 2;
        while (clientRepository.existsBySlug(slug)) {
            slug = base + "-" + next++;
        }
        return slug;
    }
}
