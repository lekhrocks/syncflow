package com.syncflow.api.security;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Security-behavior tests that assert the REAL WebSecurityConfig (JWT bearer
 * auth,
 * authenticated /api/** endpoints). Unlike the functional contract tests (which
 * use
 * a permissive test security via AbstractIntegrationTest), these load the
 * production
 * chain.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Tag("integration")
@EnabledIfSystemProperty(named = "tests.integration", matches = "true")
class ApiAuthContractTest {

    // Matches the seeded admin user in V9__users.sql and the base64 secret default.
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "admin-test-password";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("syncflow")
            .withUsername("syncflow")
            .withPassword("syncflow");

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("syncflow.encryption.key", () -> "MDEyMzQ1Njc4OWFiY2RlZg==");
        registry.add("syncflow.jwt.secret",
                () -> "c3luY2Zsb3ctaHMyNTYtand0LXNlY3JldC1rZXktMjAyNi1jaGFuZ2UtaW4tcHJvZA==");
        registry.add("syncflow.jwt.issuer", () -> "syncflow");
    }

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    private String loginToken() {
        return given().contentType(ContentType.JSON)
                .body(Map.of("username", ADMIN_USER, "password", ADMIN_PASS))
                .when().post("/api/auth/login")
                .then().statusCode(200).body("token", notNullValue())
                .extract().path("token");
    }

    @Test
    @DisplayName("POST /api/connections without auth -> 401/403")
    void createConnectionUnauthenticated() {
        given().contentType(ContentType.JSON)
                .body(Map.of("name", "test", "connectionType", "POSTGRESQL",
                        "host", "h", "port", 5432, "database", "d", "username", "u", "password", "p"))
                .when().post("/api/connections").then().statusCode(anyOf(is(401), is(403)));
    }

    @Test
    @DisplayName("POST /api/auth/login with seeded admin -> 200 + token")
    void loginSuccess() {
        given().contentType(ContentType.JSON)
                .body(Map.of("username", ADMIN_USER, "password", ADMIN_PASS))
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .body("token", notNullValue())
                .body("tokenType", equalTo("Bearer"));
    }

    @Test
    @DisplayName("POST /api/auth/login with wrong password -> 401")
    void loginBadPassword() {
        given().contentType(ContentType.JSON)
                .body(Map.of("username", ADMIN_USER, "password", "wrong"))
                .when().post("/api/auth/login")
                .then().statusCode(401);
    }

    @Test
    @DisplayName("GET /api/users with valid token -> 200")
    void usersAuthed() {
        var token = loginToken();
        given().header("Authorization", "Bearer " + token)
                .when().get("/api/users")
                .then().statusCode(200)
                .body("$", notNullValue());
    }

    @Test
    @DisplayName("GET /api/users without token -> 401")
    void usersUnauthenticated() {
        given().when().get("/api/users").then().statusCode(401);
    }
}
