package com.example.resourceserver.api;

import com.example.resourceserver.policy.WorkingHoursService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Medical API endpoints demonstrating the difference between:
 * <ul>
 *   <li><b>JWT validity</b> — signature + expiry checked by Spring Security (technical)</li>
 *   <li><b>Contextual legitimacy</b> — business working-hours check (semantic)</li>
 * </ul>
 *
 * <table border="1">
 *   <tr><th>Endpoint</th><th>Auth</th><th>Extra constraint</th></tr>
 *   <tr><td>/api/public/ping</td><td>none</td><td>—</td></tr>
 *   <tr><td>/api/medical/standard</td><td>JWT + NURSE role</td><td>none</td></tr>
 *   <tr><td>/api/medical/contextual</td><td>JWT + NURSE role</td><td>working hours</td></tr>
 *   <tr><td>/api/medical/token-info</td><td>JWT + NURSE role</td><td>none</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MedicalController {

    private final WorkingHoursService workingHoursService;

    // ─── PUBLIC ──────────────────────────────────────────────────────────────

    /** Health-check endpoint — no authentication required. */
    @GetMapping("/public/ping")
    public Map<String, String> ping() {
        return Map.of(
                "endpoint", "/api/public/ping",
                "status",   "UP",
                "message",  "Service is running — no token required"
        );
    }

    // ─── STANDARD (JWT valid = access granted) ───────────────────────────────

    /**
     * Standard protected endpoint: Spring Security verifies the JWT signature and
     * expiry only. Access is granted as long as the token is technically valid and
     * the principal holds the {@code NURSE} realm role.
     */
    @GetMapping("/medical/standard")
    public ResponseEntity<Map<String, Object>> standard(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> body = buildTokenSummary(jwt);
        body.put("endpoint",          "/api/medical/standard");
        body.put("access",            "GRANTED — JWT valid, role NURSE confirmed");
        body.put("contextual_check",  false);
        return ResponseEntity.ok(body);
    }

    // ─── CONTEXTUAL (JWT valid + within working hours) ───────────────────────

    /**
     * Contextual protected endpoint: in addition to JWT validity and the {@code NURSE}
     * role, access is only permitted during the configured working-hours window.
     * Returns {@code 403 Forbidden} outside that window even with a valid JWT.
     */
    @GetMapping("/medical/contextual")
    public ResponseEntity<Map<String, Object>> contextual(@AuthenticationPrincipal Jwt jwt) {
        if (!workingHoursService.isWithinWorkingHours()) {
            Map<String, Object> denied = new LinkedHashMap<>();
            denied.put("endpoint",   "/api/medical/contextual");
            denied.put("access",     "DENIED — outside working hours");
            denied.put("jwt_valid",  true);
            denied.put("user",       jwt.getClaimAsString("preferred_username"));
            return ResponseEntity.status(403).body(denied);
        }

        Map<String, Object> body = buildTokenSummary(jwt);
        body.put("endpoint",         "/api/medical/contextual");
        body.put("access",           "GRANTED — JWT valid + within working hours");
        body.put("contextual_check", true);
        return ResponseEntity.ok(body);
    }

    // ─── TOKEN INFO ───────────────────────────────────────────────────────────

    /**
     * Displays the decoded JWT claims for inspection/debugging.
     * Requires a valid JWT with the {@code NURSE} role.
     */
    @GetMapping("/medical/token-info")
    public ResponseEntity<Map<String, Object>> tokenInfo(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("endpoint",    "/api/medical/token-info");
        body.put("subject",     jwt.getSubject());
        body.put("username",    jwt.getClaimAsString("preferred_username"));
        body.put("email",       jwt.getClaimAsString("email"));
        body.put("issued_at",   jwt.getIssuedAt());
        body.put("expires_at",  jwt.getExpiresAt());
        body.put("realm_access", jwt.getClaimAsMap("realm_access"));
        body.put("all_claims",  jwt.getClaims());
        return ResponseEntity.ok(body);
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────────

    private Map<String, Object> buildTokenSummary(Jwt jwt) {
        Instant now       = Instant.now();
        Instant issuedAt  = jwt.getIssuedAt();
        Instant expiresAt = jwt.getExpiresAt();

        long ageSeconds       = (issuedAt  != null) ? now.getEpochSecond() - issuedAt.getEpochSecond()  : -1;
        long remainingSeconds = (expiresAt != null) ? expiresAt.getEpochSecond() - now.getEpochSecond() : -1;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("user",              jwt.getClaimAsString("preferred_username"));
        summary.put("issued_at",         issuedAt);
        summary.put("expires_at",        expiresAt);
        summary.put("age_seconds",       ageSeconds);
        summary.put("remaining_seconds", remainingSeconds);
        summary.put("token_valid",       remainingSeconds > 0);
        return summary;
    }
}
