package com.syncflow.api.agent;

import com.syncflow.agent.domain.Agent;
import com.syncflow.agent.domain.AgentId;
import com.syncflow.agent.domain.HardwareMetrics;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final FleetManager fleetManager;

    public AgentController(FleetManager fleetManager) {
        this.fleetManager = fleetManager;
    }

    @PostMapping("/register")
    public ResponseEntity<Agent> register(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        var caps = (List<String>) body.getOrDefault("capabilities", List.of());
        @SuppressWarnings("unchecked")
        var labels = (Map<String, String>) body.getOrDefault("labels", Map.of());
        var agent = fleetManager.register(
                (String) body.getOrDefault("version", "0.1.0"),
                caps, labels,
                (String) body.getOrDefault("environment", "production"),
                (String) body.getOrDefault("region", "us-east-1"),
                (String) body.getOrDefault("hostname", "unknown"));
        return ResponseEntity.ok(agent);
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<Map<String, Object>> heartbeat(@RequestBody Map<String, Object> body) {
        var id = new AgentId((String) body.get("agentId"));
        var hw = new HardwareMetrics(
                ((Number) body.getOrDefault("cpuPercent", 0)).doubleValue(),
                ((Number) body.getOrDefault("memoryUsed", 0)).longValue(),
                ((Number) body.getOrDefault("memoryTotal", 0)).longValue(),
                ((Number) body.getOrDefault("diskUsed", 0)).longValue(),
                ((Number) body.getOrDefault("diskTotal", 0)).longValue(),
                ((Number) body.getOrDefault("runningJobs", 0)).intValue(),
                ((Number) body.getOrDefault("networkRx", 0)).longValue(),
                ((Number) body.getOrDefault("networkTx", 0)).longValue());
        fleetManager.heartbeat(id, hw);
        return ResponseEntity.ok(Map.of("status", "OK", "timestamp", System.currentTimeMillis()));
    }

    @GetMapping
    public ResponseEntity<List<Agent>> list() {
        return ResponseEntity.ok(fleetManager.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Agent> get(@PathVariable String id) {
        return fleetManager.get(new AgentId(id))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/drain")
    public ResponseEntity<Map<String, Object>> drain(@PathVariable String id) {
        fleetManager.drain(new AgentId(id));
        return ResponseEntity.ok(Map.of("agentId", id, "status", "DRAINING"));
    }

    @PostMapping("/{id}/restart")
    public ResponseEntity<Map<String, Object>> restart(@PathVariable String id) {
        return ResponseEntity.ok(Map.of("agentId", id, "action", "restart_requested"));
    }

    @GetMapping("/{id}/metrics")
    public ResponseEntity<Map<String, Object>> metrics(@PathVariable String id) {
        return fleetManager.get(new AgentId(id))
                .map(a -> ResponseEntity.<Map<String, Object>>ok(Map.of(
                        "agentId", id,
                        "status", a.status().name(),
                        "cpuPercent", a.hardware().cpuPercent(),
                        "memoryUsed", a.hardware().memoryUsed(),
                        "runningJobs", a.hardware().runningJobs(),
                        "lastHeartbeat", a.lastHeartbeat().toString())))
                .orElse(ResponseEntity.notFound().build());
    }
}
