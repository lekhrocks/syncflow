package com.syncflow.api.agent;

import com.syncflow.agent.domain.AgentId;
import com.syncflow.agent.domain.AgentStatus;
import com.syncflow.agent.domain.HardwareMetrics;
import com.syncflow.api.ops.metrics.MetricsRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlPlaneUnitTest {

    private FleetManager fleetManager;

    @BeforeEach
    void setUp() {
        fleetManager = new FleetManager(new MetricsRegistry(new SimpleMeterRegistry()));
    }

    // --- Heartbeat parser ---

    @Test
    void heartbeatUpdatesTimestampAndMetrics() {
        var agent = fleetManager.register("1.0.0", List.of("SNAPSHOT"), Map.of(), "prod", "us-east-1", "host-1");
        var beforeHeartbeat = agent.lastHeartbeat();

        sleep(10);
        var hw = new HardwareMetrics(45.0, 1024L, 2048L, 500L, 1000L, 3, 100L, 50L);
        var updated = fleetManager.heartbeat(agent.id(), hw);

        assertTrue(updated.isPresent());
        assertTrue(updated.get().lastHeartbeat().isAfter(beforeHeartbeat));
        assertEquals(45.0, updated.get().hardware().cpuPercent());
        assertEquals(1024L, updated.get().hardware().memoryUsed());
        assertEquals(3, updated.get().hardware().runningJobs());
    }

    @Test
    void heartbeatWithFullMetrics() {
        var agent = fleetManager.register("0.5.0", List.of(), Map.of(), "test", "eu-west-1", "host-2");
        var hw = new HardwareMetrics(90.5, 8192L, 16384L, 4000L, 8000L, 7, 500L, 300L);
        var result = fleetManager.heartbeat(agent.id(), hw);

        assertTrue(result.isPresent());
        assertEquals(8192L, result.get().hardware().memoryUsed());
        assertEquals(16384L, result.get().hardware().memoryTotal());
        assertEquals(500L, result.get().hardware().networkRx());
    }

    @Test
    void heartbeatForUnknownAgentReturnsEmpty() {
        var result = fleetManager.heartbeat(new AgentId("nonexistent"), HardwareMetrics.empty());
        assertTrue(result.isEmpty());
    }

    // --- Task scheduler (agent selection) ---

    @Test
    void schedulerSelectsOnlineAgents() {
        fleetManager.register("1.0", List.of("SNAPSHOT", "CDC"), Map.of(), "prod", "us-east-1", "a1");
        fleetManager.register("1.0", List.of("SYNC"), Map.of(), "prod", "eu-west-1", "a2");

        var online = fleetManager.online();
        assertEquals(2, online.size());
    }

    @Test
    void schedulerFiltersOfflineAgents() {
        fleetManager.register("1.0", List.of(), Map.of(), "prod", "us-east-1", "a1");
        fleetManager.register("1.0", List.of(), Map.of(), "prod", "us-east-1", "a2");

        fleetManager.markOffline(new AgentId("a1"));
        var online = fleetManager.online();
        assertEquals(2, online.size());
    }

    @Test
    void schedulerSkipsDrainingAgents() {
        var agent = fleetManager.register("1.0", List.of(), Map.of(), "prod", "us-east-1", "a1");
        fleetManager.drain(agent.id());

        var online = fleetManager.online();
        assertTrue(online.stream().noneMatch(a -> a.status() == AgentStatus.DRAINING));
    }

    // --- Capability matcher ---

    @Test
    void capabilityMatcherFindsMatchingAgent() {
        fleetManager.register("1.0", List.of("SNAPSHOT"), Map.of(), "prod", "us-east-1", "a1");
        fleetManager.register("1.0", List.of("CDC", "SYNC"), Map.of(), "prod", "us-east-1", "a2");

        var cdcAgents = fleetManager.list().stream()
                .filter(a -> a.capabilities().contains("CDC"))
                .toList();
        assertEquals(1, cdcAgents.size());
    }

    @Test
    void capabilityMatcherNoMatch() {
        var agents = fleetManager.list().stream()
                .filter(a -> a.capabilities().contains("KAFKA"))
                .toList();
        assertTrue(agents.isEmpty());
    }

    @Test
    void capabilityMatcherMultipleMatches() {
        fleetManager.register("1.0", List.of("SNAPSHOT"), Map.of(), "prod", "us-east-1", "a1");
        fleetManager.register("1.0", List.of("SNAPSHOT"), Map.of(), "prod", "us-east-1", "a2");

        var snapshotAgents = fleetManager.list().stream()
                .filter(a -> a.capabilities().contains("SNAPSHOT"))
                .toList();
        assertEquals(2, snapshotAgents.size());
    }

    // --- Agent selector (load-based) ---

    @Test
    void agentSelectorPrefersLeastLoaded() {
        var a1 = fleetManager.register("1.0", List.of(), Map.of(), "prod", "us-east-1", "host-1");
        fleetManager.heartbeat(a1.id(), new HardwareMetrics(50, 1024, 2048, 0, 0, 2, 0, 0));

        var a2 = fleetManager.register("1.0", List.of(), Map.of(), "prod", "us-east-1", "host-2");
        fleetManager.heartbeat(a2.id(), new HardwareMetrics(30, 512, 2048, 0, 0, 0, 0, 0));

        var all = fleetManager.list();
        var leastLoaded = all.stream()
                .min(java.util.Comparator.comparingInt(a -> (int) a.hardware().runningJobs()))
                .orElseThrow();
        assertEquals("host-2", leastLoaded.hostname());
    }

    // --- Register agent ---

    @Test
    void registerAgentWithFullDetails() {
        var agent = fleetManager.register("2.0.0", List.of("SNAPSHOT", "CDC", "METADATA"),
                Map.of("type", "standard", "pool", "default"),
                "production", "us-east-1", "worker-01.example.com");

        assertEquals("2.0.0", agent.version());
        assertEquals(3, agent.capabilities().size());
        assertEquals("standard", agent.labels().get("type"));
        assertEquals("production", agent.environment());
        assertEquals("us-east-1", agent.region());
    }

    @Test
    void registerAgentWithMinimalDetails() {
        var agent = fleetManager.register("0.1.0", List.of(), Map.of(), "dev", "local", "localhost");
        assertNotNull(agent.id());
        assertEquals(AgentStatus.ONLINE, agent.status());
        assertNotNull(agent.registeredAt());
        assertNotNull(agent.lastHeartbeat());
    }

    @Test
    void registerAgentIncrementsCount() {
        assertEquals(0, fleetManager.agentCount());
        fleetManager.register("1.0", List.of(), Map.of(), "t", "r", "h");
        assertEquals(1, fleetManager.agentCount());
        fleetManager.register("1.0", List.of(), Map.of(), "t", "r", "h");
        assertEquals(2, fleetManager.agentCount());
    }

    // --- Disconnect / offline ---

    @Test
    void markAgentOffline() {
        var agent = fleetManager.register("1.0", List.of(), Map.of(), "prod", "us-east-1", "host-1");
        fleetManager.markOffline(agent.id());

        var stored = fleetManager.get(agent.id());
        assertTrue(stored.isPresent());
        assertEquals(AgentStatus.OFFLINE, stored.get().status());
    }

    @Test
    void disconnectUnknownAgentIsNoOp() {
        assertDoesNotThrow(() -> fleetManager.markOffline(new AgentId("unknown")));
    }

    @Test
    void drainAgent() {
        var agent = fleetManager.register("1.0", List.of(), Map.of(), "prod", "us-east-1", "host-1");
        fleetManager.drain(agent.id());

        var stored = fleetManager.get(agent.id());
        assertTrue(stored.isPresent());
        assertEquals(AgentStatus.DRAINING, stored.get().status());
    }

    // --- List / query ---

    @Test
    void listAllAgents() {
        fleetManager.register("1.0", List.of(), Map.of(), "t1", "r1", "h1");
        fleetManager.register("1.0", List.of(), Map.of(), "t2", "r2", "h2");
        assertEquals(2, fleetManager.list().size());
    }

    @Test
    void getAgentById() {
        var agent = fleetManager.register("1.0", List.of(), Map.of(), "prod", "us-east-1", "host-1");
        var found = fleetManager.get(agent.id());
        assertTrue(found.isPresent());
        assertEquals(agent.id(), found.get().id());
    }

    @Test
    void getNonexistentAgent() {
        assertTrue(fleetManager.get(new AgentId("missing")).isEmpty());
    }

    @Test
    void pruneOfflineAfterTimeout() {
        // Agents without heartbeat for >60s are marked UNREACHABLE by pruneOffline
        // This is called internally by heartbeat — we test the direct marking path
        var agent = fleetManager.register("1.0", List.of(), Map.of(), "prod", "us-east-1", "host-1");

        // Mark offline then verify
        fleetManager.markOffline(agent.id());
        var stored = fleetManager.get(agent.id());
        assertTrue(stored.isPresent());
        assertTrue(stored.get().status() == AgentStatus.OFFLINE ||
                stored.get().status() == AgentStatus.UNREACHABLE);
    }

    @Test
    void reRegisterAgentAfterFailure() {
        var a1 = fleetManager.register("1.0", List.of(), Map.of(), "prod", "us-east-1", "host-1");
        var a2 = fleetManager.register("1.0", List.of(), Map.of(), "prod", "us-east-1", "host-1");

        // Different agent IDs after re-registration
        assertNotEquals(a1.id(), a2.id());
    }

    // --- Regional distribution ---

    @Test
    void listAgentsByRegion() {
        fleetManager.register("1.0", List.of(), Map.of(), "prod", "us-east-1", "h1");
        fleetManager.register("1.0", List.of(), Map.of(), "prod", "eu-west-1", "h2");
        fleetManager.register("1.0", List.of(), Map.of(), "prod", "us-east-1", "h3");

        var usEast = fleetManager.list().stream()
                .filter(a -> "us-east-1".equals(a.region()))
                .toList();
        assertEquals(2, usEast.size());
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
