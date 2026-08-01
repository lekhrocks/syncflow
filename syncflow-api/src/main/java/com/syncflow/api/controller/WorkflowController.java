package com.syncflow.api.controller;

import com.syncflow.api.workflow.WorkflowScheduler;
import com.syncflow.core.workflow.WorkflowId;
import com.syncflow.core.workflow.WorkflowInstance;
import com.syncflow.core.workflow.WorkflowTask;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowScheduler scheduler;

    public WorkflowController(WorkflowScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @PostMapping
    public ResponseEntity<WorkflowInstance> create(@RequestBody Map<String, String> body) {
        var wf = scheduler.create(body.get("pipelineId"));
        return ResponseEntity.status(HttpStatus.CREATED).body(wf);
    }

    @GetMapping
    public ResponseEntity<List<WorkflowInstance>> list() {
        return ResponseEntity.ok(scheduler.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkflowInstance> get(@PathVariable String id) {
        return ResponseEntity.ok(scheduler.get(WorkflowId.from(id)));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<WorkflowInstance> start(@PathVariable String id) {
        return ResponseEntity.ok(scheduler.start(WorkflowId.from(id)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<WorkflowInstance> cancel(@PathVariable String id) {
        return ResponseEntity.ok(scheduler.cancel(WorkflowId.from(id)));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<Map<String, String>> pause(@PathVariable String id) {
        return ResponseEntity.ok(Map.of("id", id, "status", "PAUSED"));
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<Map<String, String>> resume(@PathVariable String id) {
        return ResponseEntity.ok(Map.of("id", id, "status", "RUNNING"));
    }

    @GetMapping("/{id}/graph")
    public ResponseEntity<List<WorkflowTask>> graph(@PathVariable String id) {
        var wf = scheduler.get(WorkflowId.from(id));
        return ResponseEntity.ok(wf.tasks());
    }
}
