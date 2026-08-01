package com.syncflow.api.controller;

import com.syncflow.api.snapshot.SnapshotExecutor;
import com.syncflow.core.snapshot.SnapshotJob;
import com.syncflow.core.snapshot.SnapshotProgress;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SnapshotController {

    private final SnapshotExecutor executor;

    public SnapshotController(SnapshotExecutor executor) {
        this.executor = executor;
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

    @PostMapping("/snapshots/{id}/cancel")
    public ResponseEntity<SnapshotJob> cancel(@PathVariable String id) {
        return ResponseEntity.ok(executor.cancel(id));
    }
}
