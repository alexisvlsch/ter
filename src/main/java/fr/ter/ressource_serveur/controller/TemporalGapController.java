package fr.ter.ressource_serveur.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class TemporalGapController {

    // ─── ENDPOINT PUBLIC ──────────────────────────────────────────
    // Accessible sans token — sert de référence
    @GetMapping("/public/hello")
    public Map<String, String> publicHello() {
        return Map.of(
            "endpoint",  "/public/hello",
            "acces",     "public — aucun token requis",
            "status",    "OK"
        );
    }

    // ─── ENDPOINT PROTEGE NORMAL ──────────────────────────────────
    // Nécessite un JWT valide (signature + exp)
    // Affiche les informations temporelles du token pour analyse
    @GetMapping("/protected/resource")
    public Map<String, Object> getProtectedResource(@AuthenticationPrincipal Jwt jwt) {

        Instant now        = Instant.now();
        Instant issuedAt   = jwt.getIssuedAt();
        Instant expiresAt  = jwt.getExpiresAt();

        long ageSeconds       = now.getEpochSecond() - issuedAt.getEpochSecond();
        long remainingSeconds = expiresAt.getEpochSecond() - now.getEpochSecond();

        // LinkedHashMap pour garder l'ordre dans la réponse JSON
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("endpoint",           "/protected/resource");
        response.put("status",             "200 OK — accès accordé");
        response.put("user",               jwt.getClaimAsString("preferred_username"));
        response.put("email",              jwt.getClaimAsString("email"));
        response.put("emis_a",             issuedAt.toString());
        response.put("expire_a",           expiresAt.toString());
        response.put("age_token_secondes", ageSeconds);
        response.put("validite_restante",  remainingSeconds + "s");
        response.put("token_valide",       remainingSeconds > 0);

        return response;
    }

    // ─── ENDPOINT TEMPORAL GAP ────────────────────────────────────
    // Démontre la faille :
    // Même si la session a été révoquée dans Keycloak,
    // ce endpoint répond 200 OK car Spring Boot
    // ne consulte QUE la signature et le claim exp.
    @GetMapping("/protected/temporal-gap")
    public Map<String, Object> temporalGap(@AuthenticationPrincipal Jwt jwt) {

        Instant now           = Instant.now();
        Instant expiresAt     = jwt.getExpiresAt();
        long remainingSeconds = expiresAt.getEpochSecond() - now.getEpochSecond();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("endpoint",          "/protected/temporal-gap");

        // ⚠️ Ce message s'affiche même si Keycloak a révoqué la session
        response.put("faille",            "TEMPORAL GAP CONFIRMEE");
        response.put("explication",       "Ce token est accepté même si la session Keycloak est révoquée");
        response.put("user",              jwt.getClaimAsString("preferred_username"));
        response.put("validite_restante", remainingSeconds + "s");

        // Preuves de la validation stateless
        response.put("signature_verifiee",    true);
        response.put("exp_verifie",           true);
        response.put("revocation_verifiee",   false);  // ← c'est ici la faille
        response.put("appel_keycloak",        false);  // ← aucun contact avec l'AS

        return response;
    }
}