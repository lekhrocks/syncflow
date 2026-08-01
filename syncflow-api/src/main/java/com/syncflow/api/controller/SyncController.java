package com.syncflow.api.controller;

import com.syncflow.api.sync.DeadLetterQueue;
import com.syncflow.api.sync.SyncOrchestrator;
import com.syncflow.core.sync.SyncJob;
import com.syncflow.core.sync.SyncStatistics;
import com.syncflow.core.sync.dlq.DeadLetterEvent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SyncController {

    private final SyncOrchestrator orchestrator;
    private final DeadLetterQueue dlq;

    public SyncController(SyncOrchestrator orchestrator, DeadLetterQueue dlq) {
        this.orchestrator = orchestrator;
        this.dlq = dlq;
    }

    @PostMapping("/pipelines/{id}/sync/start")
    public ResponseEntity<SyncJob> start(@PathVariable String id) {
        return ResponseEntity.ok(orchestrator.start(id));
    }

    @PostMapping("/pipelines/{id}/sync/stop")
    public ResponseEntity<Map<String, Object>> stop(@PathVariable String id) {
        orchestrator.stop(id);
        return ResponseEntity.ok(Map.of("pipelineId", id, "status", "STOPPED"));
    }

    @GetMapping("/pipelines/{id}/sync/status")
    public ResponseEntity<Map<String, Object>> status(@PathVariable String id) {
        var state = orchestrator.status(id);
        var stats = orchestrator.statistics(id);
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
        return ResponseEntity.ok(orchestrator.list());
    }

    @GetMapping("/sync/jobs/{id}")
    public ResponseEntity<SyncJob> job(@PathVariable String id) {
        return ResponseEntity.ok(orchestrator.get(id));
    }

    @GetMapping("/sync/jobs/{id}/statistics")
    public ResponseEntity<SyncStatistics> statistics(@PathVariable String id) {
        return ResponseEntity.ok(orchestrator.statistics(id));
    }

    @GetMapping("/dlq")
    public ResponseEntity<List<DeadLetterEvent>> dlqList(
            @RequestParam(required = false) String pipelineId) {
        return ResponseEntity.ok(dlq.list(pipelineId));
    }

    @PostMapping("/dlq/{id}/replay")
    public ResponseEntity<Void> replay(@PathVariable String id) {
        dlq.replay(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/dlq/{id}")
    public ResponseEntity<Void> deleteDlq(@PathVariable String id) {
        dlq.delete(id);
        return ResponseEntity.noContent().build();
    }
}
