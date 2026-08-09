package com.syncflow.core.service;

import com.syncflow.common.exception.SyncFlowException;
import com.syncflow.core.model.ConnectionConfiguration;
import com.syncflow.core.model.Pipeline;
import com.syncflow.core.model.PipelineEvent;
import com.syncflow.core.model.PipelineStatus;
import com.syncflow.core.model.TransformationConfiguration;
import com.syncflow.core.registry.ConnectorRegistry;
import com.syncflow.core.repository.PipelineRepository;
import com.syncflow.core.spi.ConnectorContext;
import com.syncflow.core.spi.ValidationResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class PipelineService {

    private final PipelineRepository repository;
    private final ConnectorRegistry connectorRegistry;
    private final List<PipelineEvent> eventLog = new ArrayList<>();

    public PipelineService(PipelineRepository repository, ConnectorRegistry connectorRegistry) {
        this.repository = repository;
        this.connectorRegistry = connectorRegistry;
    }

    public Pipeline create(String name, ConnectionConfiguration source,
            ConnectionConfiguration destination,
            TransformationConfiguration mapping) {
        var pipeline = new Pipeline(name, source, destination, mapping);
        var saved = repository.save(pipeline);
        logEvent(saved, null, PipelineStatus.CREATED, "Pipeline created");
        return saved;
    }

    public Pipeline get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> SyncFlowException.notFound("Pipeline", id));
    }

    public List<Pipeline> list() {
        return repository.findAll();
    }

    public Pipeline update(String id, String name,
            ConnectionConfiguration source,
            ConnectionConfiguration destination,
            TransformationConfiguration mapping) {
        var pipeline = get(id);
        if (pipeline.getStatus() == PipelineStatus.RUNNING) {
            throw SyncFlowException.conflict("Cannot update a running pipeline");
        }
        pipeline.setName(name);
        pipeline.setSource(source);
        pipeline.setDestination(destination);
        pipeline.setMapping(mapping);
        pipeline.setUpdatedAt(Instant.now());
        return repository.save(pipeline);
    }

    public void delete(String id) {
        var pipeline = get(id);
        if (pipeline.getStatus() == PipelineStatus.RUNNING) {
            throw SyncFlowException.conflict("Cannot delete a running pipeline");
        }
        pipeline.setStatus(PipelineStatus.DELETED);
        repository.save(pipeline);
    }

    public Pipeline start(String id) {
        var pipeline = get(id);
        if (pipeline.getStatus() == PipelineStatus.RUNNING) {
            return pipeline;
        }
        var sourceOk = validateConnection(pipeline.getSource());
        if (!sourceOk.valid()) {
            throw SyncFlowException.badRequest("Source validation failed: " +
                    String.join(", ", sourceOk.errors()));
        }
        var destOk = validateConnection(pipeline.getDestination());
        if (!destOk.valid()) {
            throw SyncFlowException.badRequest("Destination validation failed: " +
                    String.join(", ", destOk.errors()));
        }
        var prev = pipeline.getStatus();
        pipeline.setStatus(PipelineStatus.RUNNING);
        pipeline.setUpdatedAt(Instant.now());
        var saved = repository.save(pipeline);
        logEvent(saved, prev, PipelineStatus.RUNNING, "Pipeline started");
        return saved;
    }

    public Pipeline stop(String id) {
        var pipeline = get(id);
        if (pipeline.getStatus() != PipelineStatus.RUNNING) {
            return pipeline;
        }
        var prev = pipeline.getStatus();
        pipeline.setStatus(PipelineStatus.STOPPED);
        pipeline.setUpdatedAt(Instant.now());
        var saved = repository.save(pipeline);
        logEvent(saved, prev, PipelineStatus.STOPPED, "Pipeline stopped");
        return saved;
    }

    public ValidationResult validateConnection(ConnectionConfiguration config) {
        var connector = connectorRegistry.get(config.connectorType());
        if (connector.isEmpty()) {
            return ValidationResult.failed(
                    List.of("No connector registered for type: " + config.connectorType()));
        }
        var ctx = new ConnectorContext(config, null);
        return connector.get().validate(ctx);
    }

    public List<PipelineEvent> events() {
        return List.copyOf(eventLog);
    }

    private void logEvent(Pipeline pipeline, PipelineStatus previous,
            PipelineStatus next, String reason) {
        eventLog.add(new PipelineEvent(pipeline.getId(), previous, next,
                reason, Instant.now()));
    }
}
