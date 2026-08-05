package com.syncflow.api.controller;

import com.syncflow.core.registry.ConnectorRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final ConnectorRegistry connectorRegistry;

    public HealthController(ConnectorRegistry connectorRegistry) {
        this.connectorRegistry = connectorRegistry;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        var map = new LinkedHashMap<String, Object>();
        map.put("status", "UP");
        map.put("connectors", connectorRegistry.getAll().stream()
                .map(c -> {
                    var h = c.health();
                    return Map.of(
                            "type", c.type().name(),
                            "status", h.status().name(),
                            "message", h.message(),
                            "latencyMs", h.latencyMs(),
                            "capabilities", c.capabilities());
                }).toList());
        map.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(map);
    }
}
