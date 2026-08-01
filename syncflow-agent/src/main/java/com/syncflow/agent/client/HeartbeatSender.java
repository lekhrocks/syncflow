package com.syncflow.agent.client;

import com.syncflow.agent.domain.HardwareMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class HeartbeatSender {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatSender.class);

    private final HttpClient http;
    private final String controlPlaneUrl;
    private final AgentRegistrar registrar;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public HeartbeatSender(AgentRegistrar registrar,
            @org.springframework.beans.factory.annotation.Value("${syncflow.agent.control-plane:http://localhost:8080}") String cpUrl) {
        this.registrar = registrar;
        this.controlPlaneUrl = cpUrl;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::send, 0, 15, TimeUnit.SECONDS);
    }

    private void send() {
        var agentId = registrar.getAgentId();
        if (agentId == null)
            return;

        var mem = Runtime.getRuntime();
        var hw = new HardwareMetrics(
                mem.availableProcessors() > 0 ? (double) mem.freeMemory() / mem.totalMemory() * 100 : 0,
                mem.totalMemory() - mem.freeMemory(),
                mem.totalMemory(), 0, 0, 0, 0, 0);

        try {
            var body = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(Map.of(
                            "agentId", agentId.value(),
                            "cpuPercent", hw.cpuPercent(),
                            "memoryUsed", hw.memoryUsed(),
                            "memoryTotal", hw.memoryTotal(),
                            "runningJobs", hw.runningJobs()));

            var req = HttpRequest.newBuilder()
                    .uri(URI.create(controlPlaneUrl + "/api/agents/heartbeat"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            http.sendAsync(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            log.warn("Heartbeat failed: {}", e.getMessage());
        }
    }
}
