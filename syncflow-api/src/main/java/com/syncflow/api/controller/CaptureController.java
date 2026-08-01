package com.syncflow.api.controller;

import com.syncflow.api.cdc.CaptureLifecycle;
import com.syncflow.core.cdc.CaptureStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/pipelines/{id}/capture")
public class CaptureController {

    private final CaptureLifecycle lifecycle;

    public CaptureController(CaptureLifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start(@PathVariable String id) {
        var status = lifecycle.start(id, null);
        return ResponseEntity.status(status == CaptureStatus.RUNNING ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .body(Map.of("pipelineId", id, "status", status.name()));
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stop(@PathVariable String id) {
        lifecycle.stop(id);
        return ResponseEntity.ok(Map.of("pipelineId", id, "status", "STOPPED"));
    }

    @PostMapping("/pause")
    public ResponseEntity<Map<String, Object>> pause(@PathVariable String id) {
        lifecycle.pause(id);
        return ResponseEntity.ok(Map.of("pipelineId", id, "status", "PAUSED"));
    }

    @PostMapping("/resume")
    public ResponseEntity<Map<String, Object>> resume(@PathVariable String id) {
        lifecycle.resume(id);
        return ResponseEntity.ok(Map.of("pipelineId", id, "status", "RESUMED"));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(@PathVariable String id) {
        var status = lifecycle.status(id);
        var events = lifecycle.eventCount(id);
        return ResponseEntity.ok(Map.of(
                "pipelineId", id,
                "captureStatus", status.name(),
                "eventsCaptured", events));
    }
}
