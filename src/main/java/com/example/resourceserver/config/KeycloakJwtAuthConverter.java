package com.example.resourceserver.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Converts a Keycloak JWT into a Spring Security {@link AbstractAuthenticationToken}.
 * <p>
 * Keycloak stores realm roles in {@code realm_access.roles}; this converter maps each
 * role {@code X} to the Spring authority {@code ROLE_X} (upper-cased) so that
 * {@code @PreAuthorize("hasRole('NURSE')")} and {@code hasRole('NURSE')} in security
 * config work out-of-the-box.
 */
@Component
public class KeycloakJwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter defaultConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> defaultAuthorities = defaultConverter.convert(jwt);
        if (defaultAuthorities == null) {
            defaultAuthorities = Collections.emptyList();
        }

        Collection<GrantedAuthority> keycloakRoles = extractRealmRoles(jwt);

        Collection<GrantedAuthority> allAuthorities = Stream
                .concat(defaultAuthorities.stream(), keycloakRoles.stream())
                .collect(Collectors.toSet());

        return new JwtAuthenticationToken(
                jwt,
                allAuthorities,
                jwt.getClaimAsString("preferred_username")
        );
    }

    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !realmAccess.containsKey("roles")) {
            return Collections.emptyList();
        }
        List<String> roles = (List<String>) realmAccess.get("roles");
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toList());
    }
}
