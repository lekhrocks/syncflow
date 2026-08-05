package com.syncflow.api.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * JWT HS256 encoder/decoder wiring. Decoupled from the properties holder
 * ({@link JwtProperties}) so bean creation always happens after properties are
 * bound, and the secret is validated up front.
 *
 * ponytail: HS256 with one shared secret — sufficient for a single-platform
 * deployment. RS256/asymmetric is the documented upgrade path.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtSecurityConfig {

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties props) {
        var jwk = new OctetSequenceKey.Builder(secretKey(props))
                .algorithm(JWSAlgorithm.HS256)
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties props) {
        return NimbusJwtDecoder.withSecretKey(secretKey(props))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    private static SecretKeySpec secretKey(JwtProperties props) {
        var secret = props.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("syncflow.jwt.secret is not configured. "
                    + "Set a base64-encoded HS256 key (>= 32 bytes) via env SYNCFLOW_JWT_SECRET.");
        }
        final byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("syncflow.jwt.secret is not valid base64", e);
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("syncflow.jwt.secret decodes to " + bytes.length
                    + " bytes; HS256 requires at least 32 bytes (256 bits).");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
