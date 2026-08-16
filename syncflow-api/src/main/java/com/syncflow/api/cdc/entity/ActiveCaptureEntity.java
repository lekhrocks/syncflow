package com.syncflow.api.cdc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Persistent record of an active CDC capture. Replaces the in-memory
 * {@code ConcurrentHashMap} so capture state survives pod restarts and is
 * shared across replicas (HA / horizontal scaling).
 * <p>
 * Keying by {@code (tenantId, pipelineId)} ensures tenants cannot collide.
 */
@Setter
@Getter
@Entity
@Table(name = "active_captures", indexes = {
        @Index(name = "idx_active_captures_tenant", columnList = "tenant_id"),
        @Index(name = "idx_active_captures_pipeline", columnList = "pipeline_id")
})
public class ActiveCaptureEntity {

    @Id
    @Column(length = 128)
    private String id; // composite: tenantId + ":" + pipelineId (73 chars)

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "pipeline_id", nullable = false, length = 64)
    private String pipelineId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private com.syncflow.core.cdc.CaptureStatus status;

    @Column(name = "offset_data", columnDefinition = "TEXT")
    private String offsetData; // serialized offset Map

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

}
