package com.syncflow.api.sse;

import com.syncflow.api.config.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end SSE test: subscribe to the sync job event stream and verify a
 * broadcaster-emitted event reaches the HTTP client as "data:" frames.
 * Uses the permissive test security (AbstractIntegrationTest), so the
 * text/event-stream endpoints are reachable without a token.
 */
class SseIntegrationTest extends AbstractIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ssetest")
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

    @Autowired
    private StatusBroadcaster broadcaster;

    @Test
    @DisplayName("SSE sync endpoint streams broadcaster events")
    void syncEventsStream() throws Exception {
        var latch = new CountDownLatch(1);
        var received = new StringBuilder();
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/sync/jobs/p-1/events"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        // Stream the response body on a background thread; a live SSE connection
        // stays open, so read frames until the latch releases.
        var body = client.sendAsync(req, HttpResponse.BodyHandlers.ofInputStream())
                .thenAccept(resp -> {
                    assertEquals(200, resp.statusCode());
                    assertEquals("text/event-stream",
                            resp.headers().firstValue("content-type").orElse(""));
                    try (var in = resp.body()) {
                        var buf = new byte[1024];
                        int n;
                        while ((n = in.read(buf)) != -1) {
                            received.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                            if (received.toString().contains("RUNNING")) {
                                latch.countDown();
                                break;
                            }
                        }
                    } catch (Exception ignored) {
                    }
                });

        // Give the connection a moment to establish, then emit.
        Thread.sleep(500);
        broadcaster.emit("p-1", "sync-status", Map.of("state", "RUNNING", "pipelineId", "p-1"));

        assertTrue(latch.await(5, TimeUnit.SECONDS), "SSE event should be delivered");
        assertTrue(received.toString().contains("RUNNING"), "payload should carry the event data");
        body.join();
    }

    }
