package com.syncflow.api.security;

import com.syncflow.api.config.JwtProperties;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Authenticates credentials and issues a JWT whose {@code scope} claim carries
 * the user's roles (consumed by
 * {@link org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter}
 * as ROLE_ authorities).
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public AuthService(AuthenticationManager authenticationManager,
            JwtEncoder jwtEncoder,
            JwtProperties jwtProperties) {
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }

    public String login(String username, String password) {
        // authenticate() returns the populated principal (UserDetails) — carry the
        // roles from it instead of re-querying the user store.
        var auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));
        var user = (UserDetails) auth.getPrincipal();
        var roles = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring("ROLE_".length()) : a)
                .toList();
        return issueToken(user.getUsername(), roles);
    }

    private String issueToken(String username, java.util.List<String> roles) {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.getIssuer())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(jwtProperties.getExpiryMinutes() * 60))
                .subject(username)
                .claim("scope", String.join(",", roles))
                .build();
        // Pin the JWS algorithm to HS256 so Nimbus selects the matching HS256 key.
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
