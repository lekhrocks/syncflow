package com.syncflow.core.pipeline;

import com.syncflow.core.pipeline.mapping.TableMapping;

import java.time.Instant;
import java.util.List;

public record PipelineDesign(PipelineId id, PipelineName name, PipelineStatus status, SourceReference source,
        DestinationReference destination, List<TableMapping> tableMappings,
        PipelineSettings settings, AuditInformation audit) {

    public PipelineDesign(PipelineId id, PipelineName name, PipelineStatus status,
            SourceReference source, DestinationReference destination,
            List<TableMapping> tableMappings,
            PipelineSettings settings, AuditInformation audit) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.source = source;
        this.destination = destination;
        this.tableMappings = List.copyOf(tableMappings == null ? List.of() : tableMappings);
        this.settings = settings;
        this.audit = audit;
    }

    public PipelineDesign withStatus(PipelineStatus newStatus) {
        return new PipelineDesign(id, name, newStatus, source, destination,
                tableMappings, settings, audit);
    }

    public PipelineDesign withUpdatedAudit(Instant now) {
        return new PipelineDesign(id, name, status, source, destination,
                tableMappings, settings,
                new AuditInformation(audit.version(), audit.createdAt(), now, audit.createdBy()));
    }

    public PipelineDesign withVersion(int version) {
        return new PipelineDesign(id, name, status, source, destination,
                tableMappings, settings,
                new AuditInformation(version, audit.createdAt(), audit.updatedAt(), audit.createdBy()));
    }

    public static PipelineDesign create(PipelineName name, SourceReference source,
            DestinationReference destination,
            List<TableMapping> mappings,
            PipelineSettings settings) {
        var now = Instant.now();
        return new PipelineDesign(PipelineId.generate(), name, PipelineStatus.DRAFT,
                source, destination, mappings, settings,
                new AuditInformation(1, now, now, "system"));
    }
}
