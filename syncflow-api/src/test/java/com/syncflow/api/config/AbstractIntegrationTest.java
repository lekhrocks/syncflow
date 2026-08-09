package com.syncflow.api.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.Base64;

/**
 * Shared base for full-context (@SpringBootTest) integration tests.
 * Carries the Spring Boot/Testcontainers annotations, a permissive security
 * config (real chain is {@link TestSecurityConfig#testFilterChain}), and the
 * RestAssured port wiring. Subclasses declare their OWN
 * {@code @Container postgres} and {@code @DynamicPropertySource} so each test
 * keeps the exact database/credentials/data it needs.
 *
 * {@link #adminToken(String)} mints an admin JWT (subject {@code admin} =>
 * full RBAC via PolicyResolver) scoped to the given tenant — use it on
 * RBAC-guarded mutations so the tenant-aware principal is populated.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Tag("integration")
@EnabledIfSystemProperty(named = "tests.integration", matches = "true")
@Import(TestSecurityConfig.class)
public abstract class AbstractIntegrationTest {

    /** Matches the base64 secret used by the integration tests. */
    protected static final String TEST_JWT_SECRET = "c3luY2Zsb3ctaHMyNTYtand0LXNlY3JldC1rZXktMjAyNi1jaGFuZ2UtaW4tcHJvZA==";

    @LocalServerPort
    protected int port;

    @BeforeEach
    void setUpBase() {
        RestAssured.port = port;
    }

    @AfterEach
    void tearDownBase() {
        RestAssured.reset();
    }

    /**
     * Admin bearer token scoped to {@code tenantId}; subject 'admin' => full RBAC.
     */
    protected String adminToken(String tenantId) {
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(
                new OctetSequenceKey.Builder(new SecretKeySpec(
                        Base64.getDecoder().decode(TEST_JWT_SECRET), "HmacSHA256"))
                        .algorithm(JWSAlgorithm.HS256).build())));
        var claims = JwtClaimsSet.builder()
                .issuer("syncflow")
                .subject("admin")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                // The single-tenant default has a canonical UUID value; a literal
                // "default" is a DIFFERENT TenantId and would scope to nothing.
                .claim("tid", "default".equals(tenantId)
                        ? com.syncflow.tenant.TenantId.DEFAULT.value()
                        : tenantId)
                .claim("scope", "ADMIN")
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
