package com.syncflow.agent.client;

import com.syncflow.agent.domain.AgentId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class AgentRegistrar {

    private static final Logger log = LoggerFactory.getLogger(AgentRegistrar.class);

    private final HttpClient http;
    private final String controlPlaneUrl;
    private final String version;
    private AgentId agentId;

    public AgentRegistrar(
            @Value("${syncflow.agent.control-plane:http://localhost:8080}") String cpUrl,
            @Value("${syncflow.agent.version:0.1.0}") String version) {
        this.controlPlaneUrl = cpUrl;
        this.version = version;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void register() {
        try {
            var body = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(Map.of(
                            "version", version,
                            "capabilities", List.of("SNAPSHOT", "CDC", "SYNCHRONIZATION", "METADATA"),
                            "labels", Map.of("type", "standard"),
                            "environment", "customer",
                            "region", System.getenv().getOrDefault("REGION", "unknown"),
                            "hostname", java.net.InetAddress.getLocalHost().getHostName()));

            var req = HttpRequest.newBuilder()
                    .uri(URI.create(controlPlaneUrl + "/api/agents/register"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(resp.body());
                this.agentId = new AgentId(json.get("id").get("value").asText());
                log.info("Agent registered: {}", agentId);
            } else {
                log.warn("Registration failed with status: {}", resp.statusCode());
            }
        } catch (Exception e) {
            log.error("Registration error: {}", e.getMessage());
        }
    }

    public AgentId getAgentId() {
        return agentId;
    }
}
