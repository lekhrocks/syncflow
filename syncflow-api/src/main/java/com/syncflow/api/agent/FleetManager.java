package com.syncflow.api.agent;

import com.syncflow.agent.domain.Agent;
import com.syncflow.agent.domain.AgentId;
import com.syncflow.agent.domain.AgentStatus;
import com.syncflow.agent.domain.HardwareMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class FleetManager {

    private final Map<AgentId, Agent> agents = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;
    private final AtomicLong agentCounter = new AtomicLong(0);

    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(60);

    public FleetManager(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Agent register(String version, List<String> capabilities,
            Map<String, String> labels, String environment,
            String region, String hostname) {
        var agent = Agent.register(version, capabilities, labels, environment, region, hostname);
        agents.put(agent.id(), agent);
        agentCounter.incrementAndGet();
        meterRegistry.gauge("syncflow.agents.online", agents.size());
        return agent;
    }

    public Optional<Agent> heartbeat(AgentId id, HardwareMetrics hw) {
        return Optional.ofNullable(agents.computeIfPresent(id, (k, agent) -> {
            var updated = agent.withHeartbeat(hw);
            pruneOffline();
            return updated;
        }));
    }

    public void markOffline(AgentId id) {
        agents.computeIfPresent(id, (k, a) -> a.withStatus(AgentStatus.OFFLINE));
    }

    public void drain(AgentId id) {
        agents.computeIfPresent(id, (k, a) -> a.withStatus(AgentStatus.DRAINING));
    }

    public Optional<Agent> get(AgentId id) {
        return Optional.ofNullable(agents.get(id));
    }

    public List<Agent> list() {
        return List.copyOf(agents.values());
    }

    public List<Agent> online() {
        return agents.values().stream()
                .filter(a -> a.status() == AgentStatus.ONLINE)
                .toList();
    }

    public long agentCount() {
        return agentCounter.get();
    }

    private void pruneOffline() {
        var threshold = Instant.now().minus(HEARTBEAT_TIMEOUT);
        agents.values().stream()
                .filter(a -> a.lastHeartbeat().isBefore(threshold))
                .forEach(a -> agents.put(a.id(), a.withStatus(AgentStatus.UNREACHABLE)));
    }
}
