package com.syncflow.api.controller;

import com.syncflow.api.snapshot.SnapshotExecutor;
import com.syncflow.api.sse.StatusBroadcaster;
import com.syncflow.core.snapshot.SnapshotJob;
import com.syncflow.core.snapshot.SnapshotProgress;
import com.syncflow.tenant.TenantContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SnapshotController {

    private final SnapshotExecutor executor;
    private final StatusBroadcaster broadcaster;

    public SnapshotController(SnapshotExecutor executor, StatusBroadcaster broadcaster) {
        this.executor = executor;
        this.broadcaster = broadcaster;
    }

    @PostMapping("/pipelines/{id}/snapshot")
    public ResponseEntity<SnapshotJob> start(@PathVariable String id) {
        var job = executor.start(id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }

    @GetMapping("/snapshots")
    public ResponseEntity<List<SnapshotJob>> list() {
        return ResponseEntity.ok(executor.list());
    }

    @GetMapping("/snapshots/{id}")
    public ResponseEntity<SnapshotJob> get(@PathVariable String id) {
        return ResponseEntity.ok(executor.get(id));
    }

    @GetMapping("/snapshots/{id}/progress")
    public ResponseEntity<SnapshotProgress> progress(@PathVariable String id) {
        var job = executor.get(id);
        return ResponseEntity.ok(job.getProgress());
    }

    /** Live progress/status stream for a snapshot ("snapshot-status" events). */
    @GetMapping(value = "/snapshots/{id}/events", produces = "text/event-stream")
    public SseEmitter snapshotEvents(@PathVariable String id) {
        var tenant = TenantContextHolder.getTenantId().value();
        return broadcaster.subscribe(tenant + ":" + id);
    }

    @PostMapping("/snapshots/{id}/cancel")
    public ResponseEntity<SnapshotJob> cancel(@PathVariable String id) {
        return ResponseEntity.ok(executor.cancel(id));
    }
}
