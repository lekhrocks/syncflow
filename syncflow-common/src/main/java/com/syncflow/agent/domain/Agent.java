package com.syncflow.agent.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record Agent(
        AgentId id,
        String version,
        AgentStatus status,
        List<String> capabilities,
        Map<String, String> labels,
        String environment,
        String region,
        String hostname,
        HardwareMetrics hardware,
        Instant registeredAt,
        Instant lastHeartbeat) {

    public static Agent register(String version, List<String> caps,
            Map<String, String> labels, String env,
            String region, String hostname) {
        return new Agent(new AgentId(UUID.randomUUID().toString()), version,
                AgentStatus.ONLINE, caps, labels, env, region, hostname,
                HardwareMetrics.empty(), Instant.now(), Instant.now());
    }

    public Agent withHeartbeat(HardwareMetrics hw) {
        return new Agent(id, version, AgentStatus.ONLINE, capabilities, labels,
                environment, region, hostname, hw, registeredAt, Instant.now());
    }

    public Agent withStatus(AgentStatus s) {
        return new Agent(id, version, s, capabilities, labels, environment,
                region, hostname, hardware, registeredAt, lastHeartbeat);
    }
}
