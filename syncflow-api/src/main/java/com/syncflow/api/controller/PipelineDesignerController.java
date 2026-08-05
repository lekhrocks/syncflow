package com.syncflow.api.controller;

import com.syncflow.api.pipeline.PipelineDesignerService;
import com.syncflow.api.pipeline.dto.CreatePipelineDesignRequest;
import com.syncflow.api.pipeline.dto.PipelineDesignResponse;
import com.syncflow.api.pipeline.dto.UpdatePipelineDesignRequest;
import com.syncflow.core.pipeline.DestinationReference;
import com.syncflow.core.pipeline.PipelineName;
import com.syncflow.core.pipeline.PipelineSettings;
import com.syncflow.core.pipeline.SourceReference;
import com.syncflow.core.pipeline.SyncMode;
import com.syncflow.core.pipeline.mapping.TableMapping;
import com.syncflow.core.pipeline.preview.ConflictReport;
import com.syncflow.core.pipeline.preview.PipelinePreview;
import com.syncflow.core.pipeline.validation.ValidationResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pipelines")
public class PipelineDesignerController {

    private final PipelineDesignerService service;

    public PipelineDesignerController(PipelineDesignerService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PipelineDesignResponse> create(
            @Valid @RequestBody CreatePipelineDesignRequest req) {
        var name = new PipelineName(req.name());
        var source = new SourceReference(req.sourceConnectionId(), req.sourceSchema(), req.sourceTable());
        var dest = new DestinationReference(req.destConnectionId(), req.destSchema(), req.destTable(),
                req.destWriteMode());
        var syncMode = req.syncMode() != null ? req.syncMode() : SyncMode.FULL_SNAPSHOT;
        var batchSize = req.batchSize() != null ? req.batchSize() : 1000;
        var settings = req.settings() != null ? Map.copyOf(req.settings()) : Map.<String, String>of();
        var pipelineSettings = new PipelineSettings(syncMode, batchSize, 3, false, false, settings);
        var mappings = req.tableMappings() != null ? req.tableMappings() : List.<TableMapping>of();

        var pipeline = service.create(name, source, dest, mappings, pipelineSettings);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PipelineDesignResponse.from(pipeline));
    }

    @GetMapping
    public ResponseEntity<List<PipelineDesignResponse>> list() {
        var list = service.list().stream().map(PipelineDesignResponse::from).toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PipelineDesignResponse> get(@PathVariable String id) {
        return ResponseEntity.ok(PipelineDesignResponse.from(service.get(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PipelineDesignResponse> update(
            @PathVariable String id,
            @RequestBody UpdatePipelineDesignRequest req) {
        var existing = service.get(id);
        var name = req.name() != null ? new PipelineName(req.name()) : existing.name();
        var source = req.sourceConnectionId() != null
                ? new SourceReference(req.sourceConnectionId(), req.sourceSchema(), req.sourceTable())
                : existing.source();
        var dest = req.destConnectionId() != null
                ? new DestinationReference(req.destConnectionId(), req.destSchema(), req.destTable(), null)
                : existing.destination();
        var mappings = req.tableMappings() != null ? req.tableMappings() : existing.tableMappings();

        // Partial update of settings: apply only what the request sends.
        var cur = existing.settings();
        var syncMode = req.syncMode() != null ? req.syncMode() : cur.syncMode();
        var batchSize = req.batchSize() != null ? req.batchSize() : cur.batchSize();
        var settings = req.settings() != null
                ? new PipelineSettings(syncMode, batchSize, cur.maxRetries(),
                        cur.skipConstraints(), cur.skipIndexes(), req.settings())
                : new PipelineSettings(syncMode, batchSize, cur.maxRetries(),
                        cur.skipConstraints(), cur.skipIndexes(), cur.properties());

        var pipeline = service.update(id, name, source, dest, mappings, settings);
        return ResponseEntity.ok(PipelineDesignResponse.from(pipeline));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/validate")
    public ResponseEntity<ValidationResult> validate(@PathVariable String id) {
        return ResponseEntity.ok(service.validate(id));
    }

    @PostMapping("/{id}/rollback")
    public ResponseEntity<PipelineDesignResponse> rollback(
            @PathVariable String id, @RequestParam int version) {
        return ResponseEntity.ok(PipelineDesignResponse.from(service.rollback(id, version)));
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<List<PipelineDesignResponse>> versions(@PathVariable String id) {
        var list = service.versions(id).stream().map(PipelineDesignResponse::from).toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<PipelinePreview> preview(@PathVariable String id) {
        return ResponseEntity.ok(service.preview(id));
    }

    @GetMapping("/{id}/conflicts")
    public ResponseEntity<ConflictReport> conflicts(@PathVariable String id) {
        return ResponseEntity.ok(service.detectConflicts(id));
    }
}
