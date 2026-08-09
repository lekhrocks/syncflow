package com.syncflow.api.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves the P0 data-isolation fix end-to-end: data created by one tenant is
 * invisible to another tenant across the real HTTP + scoped-repository path.
 *
 * Tenant scope comes from the AUTHENTICATED PRINCIPAL (JWT {@code tid} claim),
 * not the {@code X-Tenant-Id} header — the header is only a UI hint that must
 * match the principal. Each tenant here is represented by a distinct admin JWT.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Tag("integration")
@EnabledIfSystemProperty(named = "tests.integration", matches = "true")
class TenantDataIsolationTest {

    private static final String SECRET = "c3luY2Zsb3ctaHMyNTYtand0LXNlY3JldC1rZXktMjAyNi1jaGFuZ2UtaW4tcHJvZA==";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tenantiso")
            .withUsername("testuser")
            .withPassword("testpass");

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("syncflow.encryption.key", () -> "MDEyMzQ1Njc4OWFiY2RlZg==");
        registry.add("syncflow.jwt.secret", () -> SECRET);
    }

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    private final JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(
            new OctetSequenceKey.Builder(new SecretKeySpec(Base64.getDecoder().decode(SECRET), "HmacSHA256"))
                    .algorithm(JWSAlgorithm.HS256).build())));

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    /**
     * An admin bearer token scoped to the given tenant (subject 'admin' => full
     * RBAC).
     */
    private String tokenFor(String tenantId) {
        var claims = JwtClaimsSet.builder()
                .issuer("syncflow")
                .subject("admin")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("tid", tenantId)
                .claim("scope", "ADMIN")
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private io.restassured.response.Response deleteConnection(String tenantId, String id) {
        return given().header("Authorization", "Bearer " + tokenFor(tenantId))
                .when().delete("/api/connections/{id}", id);
    }

    private void createConnection(String tenantId, String name) {
        given().header("Authorization", "Bearer " + tokenFor(tenantId))
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", name,
                        "connectionType", "POSTGRESQL",
                        "host", "localhost", "port", 5432, "database", "db",
                        "username", "u", "password", "p"))
                .when().post("/api/connections")
                .then().statusCode(201);
    }

    private void cleanup(String tenantId) {
        given().header("Authorization", "Bearer " + tokenFor(tenantId))
                .when().get("/api/connections")
                .then().statusCode(200)
                .extract().jsonPath().getList("$", Map.class)
                .forEach(row -> deleteConnection(tenantId, (String) row.get("id")).then().statusCode(204));
    }

    @Test
    @DisplayName("tenant A's connections are invisible to tenant B")
    void connectionListIsTenantScoped() {
        try {
            createConnection(TENANT_A, "a-conn");

            // Tenant B sees nothing created by tenant A.
            int tenantB = given().header("Authorization", "Bearer " + tokenFor(TENANT_B))
                    .when().get("/api/connections")
                    .then().statusCode(200)
                    .extract().jsonPath().getList("$").size();
            assertEquals(0, tenantB, "tenant B must not see tenant A's connections");

            // Tenant A sees its own.
            int tenantA = given().header("Authorization", "Bearer " + tokenFor(TENANT_A))
                    .when().get("/api/connections")
                    .then().statusCode(200)
                    .extract().jsonPath().getList("$").size();
            assertEquals(1, tenantA, "tenant A must see its own connection");
        } finally {
            cleanup(TENANT_A);
            cleanup(TENANT_B);
        }
    }

    @Test
    @DisplayName("tenant B cannot fetch tenant A's connection by id")
    void connectionGetIsTenantScoped() {
        var id = given().header("Authorization", "Bearer " + tokenFor(TENANT_A))
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "a-conn-2",
                        "connectionType", "POSTGRESQL",
                        "host", "localhost", "port", 5432, "database", "db",
                        "username", "u", "password", "p"))
                .when().post("/api/connections")
                .then().statusCode(201)
                .extract().path("id");
        try {
            // Tenant B gets a 404 for tenant A's connection.
            given().header("Authorization", "Bearer " + tokenFor(TENANT_B))
                    .when().get("/api/connections/{id}", id)
                    .then().statusCode(404);

            // Tenant A can fetch it.
            given().header("Authorization", "Bearer " + tokenFor(TENANT_A))
                    .when().get("/api/connections/{id}", id)
                    .then().statusCode(200);
        } finally {
            cleanup(TENANT_A);
            cleanup(TENANT_B);
        }
    }
}
