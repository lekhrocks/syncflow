package com.syncflow.api.pipeline.dto;

import com.syncflow.core.pipeline.DestinationReference;
import com.syncflow.core.pipeline.PipelineDesign;
import com.syncflow.core.pipeline.PipelineSettings;
import com.syncflow.core.pipeline.SourceReference;
import com.syncflow.core.pipeline.mapping.TableMapping;

import java.time.Instant;
import java.util.List;

public record PipelineDesignResponse(
        String id,
        String name,
        String status,
        int version,
        SourceReference source,
        DestinationReference destination,
        List<TableMapping> tableMappings,
        PipelineSettings settings,
        Instant createdAt,
        Instant updatedAt) {

    public static PipelineDesignResponse from(PipelineDesign d) {
        return new PipelineDesignResponse(
                d.id().value(), d.name().value(),
                d.status().name(), d.audit().version(),
                d.source(), d.destination(),
                d.tableMappings(), d.settings(),
                d.audit().createdAt(), d.audit().updatedAt());
    }
}
