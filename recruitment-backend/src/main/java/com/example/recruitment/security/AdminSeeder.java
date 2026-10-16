package com.example.recruitment.security;

import com.example.recruitment.entity.AppUser;
import com.example.recruitment.entity.Role;
import com.example.recruitment.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the first admin login on startup if it does not exist (email/password come from application.properties). */
@Component
public class AdminSeeder implements CommandLineRunner {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;

    public AdminSeeder(AppUserRepository users, PasswordEncoder encoder,
                       @Value("${app.admin.email}") String email,
                       @Value("${app.admin.password}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.email = email.trim().toLowerCase();
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (users.existsByEmailIgnoreCase(email)) return;
        AppUser admin = new AppUser();
        admin.setEmail(email);
        admin.setPasswordHash(encoder.encode(password));
        admin.setRole(Role.ADMIN);
        users.save(admin);
    }
}
