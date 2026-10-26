package com.example.recruitment.security;

import com.example.recruitment.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Who may call what.
 * ADMIN can do everything. RECRUITER, HIRING_MANAGER, MANAGER and HR_BP can read (GET) and may call the stage-update endpoint;
 * StagePermissionService decides which stage each may edit (MANAGER is view-only there, HR_BP releases offers).
 * Requirements on a client website (/api/site/requirements) can be written only by ADMIN and RECRUITER. Only ADMIN can manage users and other data.
 * A HIRING_MANAGER can only read his own client's data (see ClientScopeFilter).
 * The website editor (/api/site/settings and /api/site/logo) can be changed only by ADMIN, RECRUITER and CLIENT_ADMIN.
 * CLIENT_ADMIN (website editor) can only use /api/site/** for his own client's website (see SiteController).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** We log in through /api/auth/login, so this only stops Spring from creating a random default user. */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Not used");
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(
                Arrays.stream(origins.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList());
        config.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtService jwtService,
            AppUserRepository users,
            RoleResolver roleResolver) throws Exception {

        JwtAuthFilter jwtFilter =
                new JwtAuthFilter(jwtService, users, roleResolver);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s ->
                        s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                writeJson(res, 401, "Unauthorized",
                                        "Please log in"))
                        .accessDeniedHandler((req, res, ex) ->
                                writeJson(res, 403, "Forbidden",
                                        "You do not have permission to do this")))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/accept-invitation",
                                "/api/auth/invitation"
                        ).permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll() // client logos
                        .requestMatchers("/api/users/**").hasRole("ADMIN")
                        .requestMatchers(
                                "/api/clients/*/roles",
                                "/api/clients/*/roles/**"
                        ).hasRole("ADMIN") // role management: admin only, reads included
                        // Requirements (jobs) can be created and changed only by an admin or a recruiter.
                        // These must stay ABOVE the general /api/site/** rule below, because the first match wins.
                        .requestMatchers(HttpMethod.POST, "/api/site/requirements")
                        .hasAnyRole("ADMIN", "RECRUITER")
                        .requestMatchers(HttpMethod.PUT, "/api/site/requirements/*")
                        .hasAnyRole("ADMIN", "RECRUITER")
                        .requestMatchers(HttpMethod.PATCH, "/api/site/requirements/*/status")
                        .hasAnyRole("ADMIN", "RECRUITER")
                        // Candidates inside a client website. Same rules as /api/candidates/** above; the service checks them again.
                        // These must stay ABOVE the general /api/site/** rule below, because the first match wins.
                        .requestMatchers(HttpMethod.PUT, "/api/site/candidates/*/stages/*")
                        .hasAnyRole("ADMIN", "RECRUITER", "HIRING_MANAGER", "MANAGER", "HR_BP")
                        .requestMatchers(HttpMethod.PUT, "/api/site/candidates/*/screening")
                        .hasAnyRole("ADMIN", "RECRUITER")
                        .requestMatchers(HttpMethod.POST, "/api/site/candidates/*/screening/action")
                        .hasAnyRole("ADMIN", "RECRUITER")
                        .requestMatchers(HttpMethod.PUT, "/api/site/candidates/*/resume")
                        .hasAnyRole("ADMIN", "RECRUITER")
                        .requestMatchers(HttpMethod.POST, "/api/site/candidates/*/remarks")
                        .hasAnyRole("ADMIN", "RECRUITER")
                        .requestMatchers(HttpMethod.POST, "/api/site/requirements/*/candidates")
                        .hasAnyRole("ADMIN", "RECRUITER")
                        // Website editor (footer, name, description, logo): ADMIN, RECRUITER and CLIENT_ADMIN only.
                        // Hiring Manager, Manager and HR BP can read but not change the website.
                        .requestMatchers(HttpMethod.PUT, "/api/site/settings")
                        .hasAnyRole("ADMIN", "RECRUITER", "CLIENT_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/site/logo")
                        .hasAnyRole("ADMIN", "RECRUITER", "CLIENT_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/site/logo")
                        .hasAnyRole("ADMIN", "RECRUITER", "CLIENT_ADMIN")
                        // Everything else under /api/site/** is reading, plus the candidate and requirement rules above.
                        // The client still comes from the login token (SiteService.own), so data stays separate per client.
                        .requestMatchers("/api/site/**").hasAnyRole(
                                "ADMIN", "RECRUITER", "HIRING_MANAGER", "MANAGER", "HR_BP", "CLIENT_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/candidates/*/stages/*")
                        .hasAnyRole("ADMIN", "RECRUITER", "HIRING_MANAGER", "MANAGER", "HR_BP")
                        .requestMatchers(HttpMethod.PUT, "/api/candidates/*/screening")
                        .hasAnyRole("ADMIN", "RECRUITER")
                        .requestMatchers(HttpMethod.POST, "/api/candidates/*/screening/action")
                        .hasAnyRole("ADMIN", "RECRUITER")
                        .requestMatchers(HttpMethod.PUT, "/api/candidates/*/resume")
                        .hasAnyRole("ADMIN", "RECRUITER")
                        .requestMatchers(HttpMethod.POST, "/api/candidates/*/remarks")
                        .hasAnyRole("ADMIN", "RECRUITER")
                        .requestMatchers(HttpMethod.POST, "/api/requirements/*/candidates")
                        .hasAnyRole("ADMIN", "RECRUITER")
                        .requestMatchers(HttpMethod.GET, "/api/**").authenticated()
                        .requestMatchers("/api/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new ClientScopeFilter(), JwtAuthFilter.class);

        return http.build();
    }

    private static void writeJson(
            HttpServletResponse res,
            int status,
            String error,
            String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.getWriter().write(
                "{\"status\":" + status
                        + ",\"error\":\"" + error
                        + "\",\"message\":\"" + message + "\"}");
    }
}