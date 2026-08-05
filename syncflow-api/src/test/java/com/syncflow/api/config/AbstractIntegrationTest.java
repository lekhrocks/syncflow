package com.syncflow.api.config;

import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared base for full-context (@SpringBootTest) integration tests.
 * Carries the Spring Boot/Testcontainers annotations, a permissive security
 * config (real chain is {@link TestSecurityConfig#testFilterChain}), and the
 * RestAssured port wiring. Subclasses declare their OWN
 * {@code @Container postgres} and {@code @DynamicPropertySource} so each test
 * keeps the exact database/credentials/data it needs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Tag("integration")
@EnabledIfSystemProperty(named = "tests.integration", matches = "true")
@Import(TestSecurityConfig.class)
public abstract class AbstractIntegrationTest {

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
}
