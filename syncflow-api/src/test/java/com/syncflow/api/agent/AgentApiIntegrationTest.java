package com.syncflow.api.agent;

import com.syncflow.api.config.AbstractIntegrationTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.isA;

class AgentApiIntegrationTest extends AbstractIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("agenttest")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("syncflow.encryption.key", () -> "MDEyMzQ1Njc4OWFiY2RlZg==");
    }

    @Test
    @Order(1)
    @DisplayName("POST /agents/register -> 200 registers agent")
    void registerAgent() {
        Map<String, Object> body = new HashMap<>();
        body.put("version", "1.0.0");
        body.put("capabilities", java.util.List.of("SNAPSHOT", "CDC"));
        body.put("labels", Map.of("type", "standard"));
        body.put("environment", "production");
        body.put("region", "us-east-1");
        body.put("hostname", "agent-01");

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/api/agents/register")
                .then()
                .statusCode(200)
                .body("version", equalTo("1.0.0"))
                .body("status", equalTo("ONLINE"))
                .body("$", hasKey("id"))
                .body("region", equalTo("us-east-1"));
    }

    @Test
    @Order(2)
    @DisplayName("POST /agents/heartbeat -> 200 updates agent")
    void sendHeartbeat() {
        Map<String, Object> regBody = new HashMap<>();
        regBody.put("version", "1.0");
        regBody.put("hostname", "hb-agent");
        regBody.put("region", "us-east-1");
        regBody.put("capabilities", java.util.List.of());
        regBody.put("labels", Map.of());
        regBody.put("environment", "test");

        var agentId = given()
                .contentType(ContentType.JSON)
                .body(regBody)
                .post("/api/agents/register")
                .path("id.value");

        Map<String, Object> hbBody = new HashMap<>();
        hbBody.put("agentId", agentId);
        hbBody.put("cpuPercent", 55.0);
        hbBody.put("memoryUsed", 2048);
        hbBody.put("memoryTotal", 4096);
        hbBody.put("runningJobs", 3);

        given()
                .contentType(ContentType.JSON)
                .body(hbBody)
                .when().post("/api/agents/heartbeat")
                .then()
                .statusCode(200)
                .body("status", equalTo("OK"));
    }

    @Test
    @Order(3)
    @DisplayName("GET /agents -> 200 lists agents")
    void listAgents() {
        given()
                .when().get("/api/agents")
                .then()
                .statusCode(200)
                .body("$", isA(java.util.List.class));
    }

    @Test
    @Order(4)
    @DisplayName("GET /agents/{id} -> 200 returns agent")
    void getAgent() {
        Map<String, Object> regBody = new HashMap<>();
        regBody.put("version", "1.0");
        regBody.put("hostname", "get-agent");
        regBody.put("region", "us-east-1");
        regBody.put("capabilities", java.util.List.of());
        regBody.put("labels", Map.of());
        regBody.put("environment", "test");

        var agentId = given()
                .contentType(ContentType.JSON)
                .body(regBody)
                .post("/api/agents/register")
                .path("id.value");

        given()
                .when().get("/api/agents/{id}", agentId)
                .then()
                .statusCode(200)
                .body("id.value", equalTo(agentId));
    }

    @Test
    @Order(5)
    @DisplayName("POST /agents/{id}/drain -> 200 marks draining")
    void drainAgent() {
        Map<String, Object> regBody = new HashMap<>();
        regBody.put("version", "1.0");
        regBody.put("hostname", "drain-agent");
        regBody.put("region", "us-east-1");
        regBody.put("capabilities", java.util.List.of());
        regBody.put("labels", Map.of());
        regBody.put("environment", "test");

        var agentId = given()
                .contentType(ContentType.JSON)
                .body(regBody)
                .post("/api/agents/register")
                .path("id.value");

        given()
                .when().post("/api/agents/{id}/drain", agentId)
                .then()
                .statusCode(200)
                .body("status", equalTo("DRAINING"));
    }

    @Test
    @Order(6)
    @DisplayName("GET /agents/{id}/metrics -> 200 returns metrics")
    void getAgentMetrics() {
        Map<String, Object> regBody = new HashMap<>();
        regBody.put("version", "1.0");
        regBody.put("hostname", "metrics-agent");
        regBody.put("region", "us-east-1");
        regBody.put("capabilities", java.util.List.of());
        regBody.put("labels", Map.of());
        regBody.put("environment", "test");

        var agentId = given()
                .contentType(ContentType.JSON)
                .body(regBody)
                .post("/api/agents/register")
                .path("id.value");

        given()
                .when().get("/api/agents/{id}/metrics", agentId)
                .then()
                .statusCode(200)
                .body("$", hasKey("agentId"))
                .body("$", hasKey("status"));
    }

    @Test
    @Order(7)
    @DisplayName("GET /agents/{id} -> 404 for unknown")
    void getNonexistentAgent() {
        given()
                .when().get("/api/agents/nonexistent")
                .then()
                .statusCode(404);
    }
}
