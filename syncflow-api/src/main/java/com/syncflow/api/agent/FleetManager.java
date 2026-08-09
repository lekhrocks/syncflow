package com.syncflow.api.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.syncflow.agent.domain.Agent;
import com.syncflow.agent.domain.AgentId;
import com.syncflow.agent.domain.AgentStatus;
import com.syncflow.agent.domain.HardwareMetrics;
import com.syncflow.api.agent.entity.AgentEntity;
import com.syncflow.api.agent.repository.AgentRepository;
import com.syncflow.api.ops.metrics.MetricsRegistry;
import com.syncflow.api.runtimestate.RuntimeStateJson;
import com.syncflow.tenant.TenantSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    private final AgentRepository repository;
    private final RuntimeStateJson json;
    private final MetricsRegistry metrics;

    // Fast-path cache; durable source of truth is the agents table.
    private final Map<AgentId, Agent> agents = new ConcurrentHashMap<>();
    private final AtomicLong agentCounter = new AtomicLong(0);
    private final Map<String, LongAdder> onlineByRegion = new ConcurrentHashMap<>();

    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(60);

    @Autowired
    public FleetManager(AgentRepository repository, RuntimeStateJson json, MetricsRegistry metrics) {
        this.repository = repository;
        this.json = json;
        this.metrics = metrics;
    }

    /** Unit-test seam: in-memory fleet without a repository. */
    public FleetManager(MetricsRegistry metrics) {
        this.repository = null;
        this.json = null;
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

    @Transactional
    public Agent register(String version, List<String> capabilities,
            Map<String, String> labels, String environment,
            String region, String hostname) {
        var agent = Agent.register(version, capabilities, labels, environment, region, hostname);
        agents.put(agent.id(), agent);
        agentCounter.incrementAndGet();
        incOnline(region);
        persist(agent);
        return agent;
    }

    @Transactional
    public Optional<Agent> heartbeat(AgentId id, HardwareMetrics hw) {
        return Optional.ofNullable(agents.computeIfPresent(id, (k, agent) -> {
            var updated = agent.withHeartbeat(hw);
            pruneOffline();
            persist(updated);
            return updated;
        }));
    }

    @Transactional
    public void markOffline(AgentId id) {
        agents.computeIfPresent(id, (k, a) -> {
            if (a.status() == AgentStatus.ONLINE)
                decOnline(a.region());
            var updated = a.withStatus(AgentStatus.OFFLINE);
            persist(updated);
            return updated;
        });
    }

    @Transactional
    public void drain(AgentId id) {
        agents.computeIfPresent(id, (k, a) -> {
            if (a.status() == AgentStatus.ONLINE)
                decOnline(a.region());
            var updated = a.withStatus(AgentStatus.DRAINING);
            persist(updated);
            return updated;
        });
    }

    @Transactional(readOnly = true)
    public Optional<Agent> get(AgentId id) {
        return Optional.ofNullable(agents.get(id));
    }

    @Transactional(readOnly = true)
    public List<Agent> list() {
        if (repository != null)
            return repository.findByTenantId(TenantSupport.tenantId()).stream()
                    .map(this::toDomain)
                    .toList();
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
                    var updated = a.withStatus(AgentStatus.UNREACHABLE);
                    agents.put(a.id(), updated);
                    persist(updated);
                });
    }

    private void persist(Agent agent) {
        if (repository == null)
            return; // unit-test seam
        var entity = repository.findById(agent.id().value()).orElseGet(AgentEntity::new);
        entity.setId(agent.id().value());
        entity.setTenantId(TenantSupport.tenantId());
        entity.setVersion(agent.version());
        entity.setStatus(agent.status().name());
        entity.setCapabilities(json.toJson(agent.capabilities()));
        entity.setLabels(json.toJson(agent.labels()));
        entity.setEnvironment(agent.environment());
        entity.setRegion(agent.region());
        entity.setHostname(agent.hostname());
        entity.setHardware(json.toJson(agent.hardware()));
        entity.setRegisteredAt(agent.registeredAt());
        entity.setLastHeartbeat(agent.lastHeartbeat());
        entity.setCreatedAt(agent.registeredAt());
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
    }

    private Agent toDomain(AgentEntity e) {
        return new Agent(new AgentId(e.getId()), e.getVersion(),
                AgentStatus.valueOf(e.getStatus()),
                json.fromJson(e.getCapabilities(), new TypeReference<List<String>>() {
                }),
                json.fromJson(e.getLabels(), new TypeReference<Map<String, String>>() {
                }),
                e.getEnvironment(), e.getRegion(), e.getHostname(),
                json.fromJson(e.getHardware(), HardwareMetrics.class),
                e.getRegisteredAt(), e.getLastHeartbeat());
    }
}
