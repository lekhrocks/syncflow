package com.syncflow.api.ops.dashboard;

import com.syncflow.api.connection.service.ConnectionService;
import com.syncflow.api.ops.alert.AlertEngine;
import com.syncflow.api.ops.audit.AuditStore;
import com.syncflow.api.pipeline.PipelineDesignerService;
import com.syncflow.api.snapshot.SnapshotExecutor;
import com.syncflow.api.sync.DeadLetterQueue;
import com.syncflow.api.sync.SyncOrchestrator;
import com.syncflow.core.connection.Connection;
import com.syncflow.core.pipeline.PipelineDesign;
import com.syncflow.core.registry.ConnectorRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final PipelineDesignerService pipelineService;
    private final ConnectionService connectionService;
    private final ConnectorRegistry connectorRegistry;
    private final SyncOrchestrator syncOrchestrator;
    private final SnapshotExecutor snapshotExecutor;
    private final AlertEngine alertEngine;
    private final AuditStore auditStore;
    private final DeadLetterQueue dlq;

    public DashboardController(PipelineDesignerService pipelineService,
            ConnectionService connectionService,
            ConnectorRegistry connectorRegistry,
            SyncOrchestrator syncOrchestrator,
            SnapshotExecutor snapshotExecutor,
            AlertEngine alertEngine,
            AuditStore auditStore,
            DeadLetterQueue dlq) {
        this.pipelineService = pipelineService;
        this.connectionService = connectionService;
        this.connectorRegistry = connectorRegistry;
        this.syncOrchestrator = syncOrchestrator;
        this.snapshotExecutor = snapshotExecutor;
        this.alertEngine = alertEngine;
        this.auditStore = auditStore;
        this.dlq = dlq;
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> overview() {
        var pipelines = pipelineService.list();
        var connections = connectionService.list();
        var syncJobs = syncOrchestrator.list();
        var snapshots = snapshotExecutor.list();

        return ResponseEntity.ok(new LinkedHashMap<>() {

            {
                put("pipelines", new LinkedHashMap<>() {

                    {
                        put("total", pipelines.size());
                        put("draft", pipelines.stream().filter(p -> p.status().name().equals("DRAFT")).count());
                        put("validated",
                                pipelines.stream().filter(p -> p.status().name().equals("VALIDATED")).count());
                    }
                });
                put("connections", new LinkedHashMap<>() {

                    {
                        put("total", connections.size());
                        put("postgresql", connections.stream()
                                .filter(c -> c.getProperties().type().name().equals("POSTGRESQL")).count());
                        put("mysql", connections.stream().filter(c -> c.getProperties().type().name().equals("MYSQL"))
                                .count());
                        put("mongodb", connections.stream()
                                .filter(c -> c.getProperties().type().name().equals("MONGODB")).count());
                        put("redis", connections.stream().filter(c -> c.getProperties().type().name().equals("REDIS"))
                                .count());
                    }
                });
                put("connectors", connectorRegistry.getAll().size());
                put("syncJobs", new LinkedHashMap<>() {

                    {
                        put("total", syncJobs.size());
                        put("running", syncJobs.stream().filter(j -> j.getState().name().equals("RUNNING")).count());
                    }
                });
                put("snapshots", new LinkedHashMap<>() {

                    {
                        put("total", snapshots.size());
                        put("running", snapshots.stream().filter(j -> j.getStatus().name().equals("RUNNING")).count());
                        put("completed",
                                snapshots.stream().filter(j -> j.getStatus().name().equals("COMPLETED")).count());
                        put("failed", snapshots.stream().filter(j -> j.getStatus().name().equals("FAILED")).count());
                    }
                });
                put("alerts", alertEngine.active().size());
                put("dlq", dlq.count());
                put("auditEvents", auditStore.count());
            }
        });
    }

    @GetMapping("/pipelines")
    public ResponseEntity<List<PipelineDesign>> pipelines() {
        return ResponseEntity.ok(pipelineService.list());
    }

    @GetMapping("/connections")
    public ResponseEntity<List<Connection>> connections() {
        return ResponseEntity.ok(connectionService.list());
    }

    @GetMapping("/connectors")
    public ResponseEntity<Map<String, Object>> connectors() {
        var info = new LinkedHashMap<String, Object>();
        connectorRegistry.getAll().forEach(c -> info.put(c.type().name(), Map.of(
                "capabilities", c.capabilities(),
                "health", c.health().status().name())));
        return ResponseEntity.ok(info);
    }

    @GetMapping("/jobs")
    public ResponseEntity<Map<String, Object>> jobs() {
        return ResponseEntity.ok(Map.of(
                "sync", syncOrchestrator.list().size(),
                "snapshots", snapshotExecutor.list().size()));
    }

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> metrics() {
        return ResponseEntity.ok(Map.of(
                "alerts", alertEngine.count(),
                "auditEvents", auditStore.count(),
                "dlqSize", dlq.count()));
    }

    @GetMapping("/errors")
    public ResponseEntity<List<Object>> errors() {
        var snapshotFailures = snapshotExecutor.list().stream()
                .filter(j -> j.getStatus().name().equals("FAILED"))
                .map(j -> (Object) Map.of("type", "SNAPSHOT",
                        "id", j.getId().value(), "pipelineId", j.getPipelineId(),
                        "errors", j.getErrors()))
                .toList();
        return ResponseEntity.ok(snapshotFailures);
    }
}
