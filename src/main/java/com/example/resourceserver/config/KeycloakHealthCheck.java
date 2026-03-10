package com.example.resourceserver.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Checks at startup that Keycloak is reachable and that the OpenID Connect
 * Discovery endpoint responds correctly.
 * <p>
 * This check is non-blocking: the server starts even if Keycloak is unavailable,
 * but a clear warning is printed in the logs so the issue is immediately visible.
 */
@Component
public class KeycloakHealthCheck {

    private static final Logger log = LoggerFactory.getLogger(KeycloakHealthCheck.class);
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS    = 3000;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @PostConstruct
    public void checkKeycloakAvailability() {
        String oidcConfigUrl = issuerUri + "/.well-known/openid-configuration";
        log.info("Vérification de la disponibilité de Keycloak : {}", oidcConfigUrl);

        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(oidcConfigUrl).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod("GET");

            int status = connection.getResponseCode();
            connection.disconnect();

            if (status == 200) {
                log.info("✅ Keycloak est disponible et répond correctement (HTTP {}) sur : {}",
                        status, oidcConfigUrl);
            } else {
                log.warn("⚠️  Keycloak a répondu avec le code HTTP {} sur : {}. "
                        + "Assurez-vous que l'issuer '{}' est correct et que Keycloak est bien configuré.",
                        status, oidcConfigUrl, issuerUri);
            }
        } catch (IOException e) {
            log.warn("⚠️  Impossible de joindre Keycloak sur : {}", oidcConfigUrl);
            log.warn("   Erreur : {}", e.getMessage());
            log.warn("   → Le serveur Spring Boot va démarrer, mais toute requête avec un JWT échouera"
                    + " tant que Keycloak ne sera pas disponible.");
            log.warn("   → Vérifiez que Keycloak est démarré et que l'issuer '{}' est accessible.",
                    issuerUri);
        }
    }
}
