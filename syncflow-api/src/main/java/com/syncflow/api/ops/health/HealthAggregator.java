package com.syncflow.api.ops.health;

import com.syncflow.api.cdc.CaptureLifecycle;
import com.syncflow.api.connection.service.ConnectionService;
import com.syncflow.api.sync.SyncOrchestrator;
import com.syncflow.core.registry.ConnectorRegistry;
import com.syncflow.core.spi.ConnectorHealth;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class HealthAggregator {

    private final ConnectorRegistry connectorRegistry;
    private final ConnectionService connectionService;
    private final CaptureLifecycle captureLifecycle;
    private final SyncOrchestrator syncOrchestrator;

    public HealthAggregator(ConnectorRegistry connectorRegistry,
            ConnectionService connectionService,
            CaptureLifecycle captureLifecycle,
            SyncOrchestrator syncOrchestrator) {
        this.connectorRegistry = connectorRegistry;
        this.connectionService = connectionService;
        this.captureLifecycle = captureLifecycle;
        this.syncOrchestrator = syncOrchestrator;
    }

    public Map<String, Object> aggregate() {
        var result = new LinkedHashMap<String, Object>();
        result.put("status", overallStatus());
        result.put("connectors", connectorHealth());
        result.put("connections", connectionHealth());
        result.put("captures", captureHealth());
        result.put("syncs", syncHealth());
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    private String overallStatus() {
        var anyDown = connectorRegistry.getAll().stream()
                .anyMatch(c -> c.health().status() == ConnectorHealth.Status.DOWN);
        return anyDown ? "DEGRADED" : "UP";
    }

    private List<Map<String, Object>> connectorHealth() {
        return connectorRegistry.getAll().stream().<Map<String, Object>>map(c -> {
            var h = c.health();
            var m = new LinkedHashMap<String, Object>();
            m.put("type", c.type().name());
            m.put("status", h.status().name());
            m.put("message", h.message());
            m.put("latencyMs", h.latencyMs());
            return m;
        }).toList();
    }

    private Map<String, Object> connectionHealth() {
        try {
            var count = connectionService.list().size();
            return Map.of("status", "UP", "total", count);
        } catch (Exception e) {
            return Map.of("status", "DOWN", "error", e.getMessage());
        }
    }

    private List<Map<String, Object>> captureHealth() {
        // ponytail: capture health per active pipeline tracked via CaptureLifecycle
        return new ArrayList<Map<String, Object>>();
    }

    private Map<String, Object> syncHealth() {
        var jobs = syncOrchestrator.list();
        var running = jobs.stream().filter(j -> j.getState().name().equals("RUNNING")).count();
        return Map.of("activeJobs", jobs.size(), "running", running);
    }
}
