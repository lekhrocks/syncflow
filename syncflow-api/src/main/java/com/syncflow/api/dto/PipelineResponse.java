package com.syncflow.api.dto;

import com.syncflow.core.model.ConnectionConfiguration;
import com.syncflow.core.model.Pipeline;
import com.syncflow.core.model.PipelineStatus;
import com.syncflow.core.model.TransformationConfiguration;

import java.time.Instant;

public record PipelineResponse(
        String id,
        String name,
        PipelineStatus status,
        ConnectionConfiguration source,
        ConnectionConfiguration destination,
        TransformationConfiguration mapping,
        Instant createdAt,
        Instant updatedAt) {

    public static PipelineResponse from(Pipeline p) {
        return new PipelineResponse(p.getId(), p.getName(), p.getStatus(),
                p.getSource(), p.getDestination(), p.getMapping(),
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
