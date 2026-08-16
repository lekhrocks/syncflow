package com.syncflow.api.controller;

import com.syncflow.api.security.rbac.AuthorizationService;
import com.syncflow.api.security.rbac.ResourcePermission;
import com.syncflow.api.sse.StatusBroadcaster;
import com.syncflow.api.sync.DeadLetterQueue;
import com.syncflow.api.sync.SyncOrchestrator;
import com.syncflow.core.sync.SyncJob;
import com.syncflow.core.sync.SyncStatistics;
import com.syncflow.core.sync.dlq.DeadLetterEvent;
import com.syncflow.tenant.TenantContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SyncController {

    private final SyncOrchestrator orchestrator;
    private final DeadLetterQueue dlq;
    private final StatusBroadcaster broadcaster;
    private final AuthorizationService authz;

    public SyncController(SyncOrchestrator orchestrator, DeadLetterQueue dlq,
            StatusBroadcaster broadcaster, AuthorizationService authz) {
        this.orchestrator = orchestrator;
        this.dlq = dlq;
        this.broadcaster = broadcaster;
        this.authz = authz;
    }

    /**
     * Live status stream for a sync job or pipeline: emits a "sync-status" event
     * on every state/statistics change. The client uses EventSource; provide:
     * {@code sync-status} events.
     */
    @GetMapping(value = "/sync/jobs/{id}/events", produces = "text/event-stream")
    public SseEmitter syncEvents(@PathVariable String id) {
        authz.require(ResourcePermission.EXECUTION_READ);
        var tenantContext = TenantContextHolder.get();
        return broadcaster.subscribe(tenantContext.tenantId().value() + ":" + id);
    }

    @PostMapping("/pipelines/{id}/sync/start")
    public ResponseEntity<SyncJob> start(@PathVariable String id) {
        authz.require(ResourcePermission.PIPELINE_EXECUTE);
        var tenantContext = TenantContextHolder.get();
        return ResponseEntity.ok(orchestrator.start(id, tenantContext));
    }

    @PostMapping("/pipelines/{id}/sync/stop")
    public ResponseEntity<Map<String, Object>> stop(@PathVariable String id) {
        authz.require(ResourcePermission.PIPELINE_EXECUTE);
        var tenantContext = TenantContextHolder.get();
        orchestrator.stop(id, tenantContext);
        return ResponseEntity.ok(Map.of("pipelineId", id, "status", "STOPPED"));
    }

    @GetMapping("/pipelines/{id}/sync/status")
    public ResponseEntity<Map<String, Object>> status(@PathVariable String id) {
        authz.require(ResourcePermission.EXECUTION_READ);
        var tenantContext = TenantContextHolder.get();
        var state = orchestrator.status(id, tenantContext);
        var stats = orchestrator.statistics(id, tenantContext);
        return ResponseEntity.ok(Map.of(
                "pipelineId", id,
                "state", state.name(),
                "processedEvents", stats.processedEvents(),
                "failedEvents", stats.failedEvents(),
                "skippedEvents", stats.skippedEvents(),
                "retries", stats.retries()));
    }

    @GetMapping("/sync/jobs")
    public ResponseEntity<List<SyncJob>> jobs() {
        authz.require(ResourcePermission.EXECUTION_READ);
        var tenantContext = TenantContextHolder.get();
        return ResponseEntity.ok(orchestrator.list(tenantContext));
    }

    @GetMapping("/sync/jobs/{id}")
    public ResponseEntity<SyncJob> job(@PathVariable String id) {
        authz.require(ResourcePermission.EXECUTION_READ);
        var tenantContext = TenantContextHolder.get();
        return ResponseEntity.ok(orchestrator.get(id, tenantContext));
    }

    @GetMapping("/sync/jobs/{id}/statistics")
    public ResponseEntity<SyncStatistics> statistics(@PathVariable String id) {
        authz.require(ResourcePermission.EXECUTION_READ);
        var tenantContext = TenantContextHolder.get();
        return ResponseEntity.ok(orchestrator.statistics(id, tenantContext));
    }

    @GetMapping("/dlq")
    public ResponseEntity<List<DeadLetterEvent>> dlqList(
            @RequestParam(required = false) String pipelineId) {
        authz.require(ResourcePermission.EXECUTION_READ);
        var tenantContext = TenantContextHolder.get();
        return ResponseEntity.ok(dlq.list(pipelineId, tenantContext));
    }

    @PostMapping("/dlq/{id}/replay")
    public ResponseEntity<Void> replay(@PathVariable String id) {
        authz.require(ResourcePermission.PIPELINE_EXECUTE);
        dlq.replay(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/dlq/{id}")
    public ResponseEntity<Void> deleteDlq(@PathVariable String id) {
        authz.require(ResourcePermission.PIPELINE_DELETE);
        dlq.delete(id);
        return ResponseEntity.noContent().build();
    }
}
