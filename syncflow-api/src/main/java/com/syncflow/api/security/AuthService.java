package com.syncflow.api.security;

import com.syncflow.api.config.JwtProperties;
import com.syncflow.api.user.repository.UserRepository;
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
    private final UserRepository userRepository;

    public AuthService(AuthenticationManager authenticationManager,
            JwtEncoder jwtEncoder,
            JwtProperties jwtProperties,
            UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.userRepository = userRepository;
    }

    /**
     * Result of a successful login: the bearer token + whether the password must
     * change.
     */
    public record LoginResult(String token, boolean mustChangePassword) {
    }

    public LoginResult login(String username, String password) {
        // authenticate() returns the populated principal (UserDetails) — carry the
        // roles from it instead of re-querying the user store.
        var auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));
        var user = (UserDetails) auth.getPrincipal();
        var roles = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring("ROLE_".length()) : a)
                .toList();
        var mustChangePassword = userRepository.findByUsername(user.getUsername())
                .map(u -> u.isMustChangePassword())
                .orElse(false);
        return new LoginResult(issueToken(user.getUsername(), roles), mustChangePassword);
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
