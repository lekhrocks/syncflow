package com.syncflow.api.controller;

import com.syncflow.api.security.rbac.AuthorizationService;
import com.syncflow.api.security.rbac.ResourcePermission;
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
    private final AuthorizationService authz;

    public WorkflowController(WorkflowScheduler scheduler, AuthorizationService authz) {
        this.scheduler = scheduler;
        this.authz = authz;
    }

    @PostMapping
    public ResponseEntity<WorkflowInstance> create(@RequestBody Map<String, String> body) {
        authz.require(ResourcePermission.PIPELINE_WRITE);
        var wf = scheduler.create(body.get("pipelineId"));
        return ResponseEntity.status(HttpStatus.CREATED).body(wf);
    }

    @GetMapping
    public ResponseEntity<List<WorkflowInstance>> list() {
        authz.require(ResourcePermission.EXECUTION_READ);
        return ResponseEntity.ok(scheduler.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkflowInstance> get(@PathVariable String id) {
        authz.require(ResourcePermission.EXECUTION_READ);
        return ResponseEntity.ok(scheduler.get(WorkflowId.from(id)));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<WorkflowInstance> start(@PathVariable String id) {
        authz.require(ResourcePermission.PIPELINE_EXECUTE);
        return ResponseEntity.ok(scheduler.start(WorkflowId.from(id)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<WorkflowInstance> cancel(@PathVariable String id) {
        authz.require(ResourcePermission.EXECUTION_CANCEL);
        return ResponseEntity.ok(scheduler.cancel(WorkflowId.from(id)));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<Map<String, String>> pause(@PathVariable String id) {
        authz.require(ResourcePermission.PIPELINE_EXECUTE);
        return ResponseEntity.ok(Map.of("id", id, "status", "PAUSED"));
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<Map<String, String>> resume(@PathVariable String id) {
        authz.require(ResourcePermission.PIPELINE_EXECUTE);
        return ResponseEntity.ok(Map.of("id", id, "status", "RUNNING"));
    }

    @GetMapping("/{id}/graph")
    public ResponseEntity<List<WorkflowTask>> graph(@PathVariable String id) {
        authz.require(ResourcePermission.EXECUTION_READ);
        var wf = scheduler.get(WorkflowId.from(id));
        return ResponseEntity.ok(wf.tasks());
    }
}
