package com.syncflow.api.agent;

import com.syncflow.agent.domain.Agent;
import com.syncflow.agent.domain.AgentId;
import com.syncflow.agent.domain.AgentStatus;
import com.syncflow.agent.domain.HardwareMetrics;
import com.syncflow.api.ops.metrics.MetricsRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

@Component
public class FleetManager {

    private final Map<AgentId, Agent> agents = new ConcurrentHashMap<>();
    private final MetricsRegistry metrics;
    private final AtomicLong agentCounter = new AtomicLong(0);
    private final Map<String, LongAdder> onlineByRegion = new ConcurrentHashMap<>();

    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(60);

    public FleetManager(MetricsRegistry metrics) {
        this.metrics = metrics;
    }

    private void incOnline(String region) {
        onlineByRegion.computeIfAbsent(region, r -> {
            var adder = new LongAdder();
            metrics.gauge("syncflow.agents.online", adder,
                    a -> a.doubleValue(), "region", r);
            return adder;
        }).increment();
    }

    private void decOnline(String region) {
        var adder = onlineByRegion.get(region);
        if (adder != null)
            adder.decrement();
    }

    public Agent register(String version, List<String> capabilities,
            Map<String, String> labels, String environment,
            String region, String hostname) {
        var agent = Agent.register(version, capabilities, labels, environment, region, hostname);
        agents.put(agent.id(), agent);
        agentCounter.incrementAndGet();
        incOnline(region);
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
        agents.computeIfPresent(id, (k, a) -> {
            if (a.status() == AgentStatus.ONLINE)
                decOnline(a.region());
            return a.withStatus(AgentStatus.OFFLINE);
        });
    }

    public void drain(AgentId id) {
        agents.computeIfPresent(id, (k, a) -> {
            if (a.status() == AgentStatus.ONLINE)
                decOnline(a.region());
            return a.withStatus(AgentStatus.DRAINING);
        });
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
                .filter(a -> a.status() == AgentStatus.ONLINE
                        && a.lastHeartbeat().isBefore(threshold))
                .forEach(a -> {
                    decOnline(a.region());
                    agents.put(a.id(), a.withStatus(AgentStatus.UNREACHABLE));
                });
    }
}
