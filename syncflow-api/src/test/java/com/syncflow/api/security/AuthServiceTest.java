package com.syncflow.api.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.syncflow.api.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final String SECRET = "c3luY2Zsb3ctaHMyNTYtand0LXNlY3JldC1rZXktMjAyNi1jaGFuZ2UtaW4tcHJvZA==";

    private AuthenticationManager authenticationManager;
    private AuthService service;

    @BeforeEach
    void setUp() {
        authenticationManager = mock(AuthenticationManager.class);
        var props = new JwtProperties();
        props.setSecret(SECRET);
        props.setIssuer("syncflow");
        props.setExpiryMinutes(60);
        var key = new SecretKeySpec(Base64.getDecoder().decode(SECRET), "HmacSHA256");
        var jwk = new OctetSequenceKey.Builder(key)
                .algorithm(JWSAlgorithm.HS256)
                .build();
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        service = new AuthService(authenticationManager, encoder, props);
    }

    @Test
    void loginIssuesJwtWithScopeClaim() {
        var principal = User.withUsername("admin")
                .password("pw")
                .authorities("ROLE_ADMIN")
                .build();
        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        var token = service.login("admin", "pw");
        assertNotNull(token);
        var parts = token.split("\\.");
        assertEquals(3, parts.length, "JWT should have three segments");
        var claims = new String(Base64.getUrlDecoder().decode(parts[1]));
        assertTrue(claims.contains("syncflow"), "issuer should be present");
        assertTrue(claims.contains("ADMIN"), "scope claim should carry roles");
    }

    @Test
    void scopesStripRolePrefix() {
        var principal = User.withUsername("bob")
                .password("pw")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        var token = service.login("bob", "pw");
        var claims = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        assertTrue(claims.contains("\"USER\""), "scope should be bare role, not ROLE_-prefixed");
    }
}
