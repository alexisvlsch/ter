package com.example.resourceserver.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the resource server.
 *
 * <ul>
 *   <li>{@code /api/public/**} — no authentication required</li>
 *   <li>{@code /api/medical/**} — valid JWT + realm role {@code NURSE} required</li>
 *   <li>All other requests — authenticated JWT required</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final KeycloakJwtAuthConverter keycloakJwtAuthConverter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF protection is disabled intentionally: this is a stateless REST resource
            // server that authenticates via Bearer JWT tokens in the Authorization header.
            // CSRF attacks rely on cookies being automatically sent by browsers; since this
            // API uses Authorization headers (not cookies), it is not vulnerable to CSRF.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**", "/actuator/health", "/error").permitAll()
                .requestMatchers("/api/medical/**").hasRole("NURSE")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtAuthConverter))
            );

        return http.build();
    }
}
