package com.syncflow.api.sync.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Sync job runtime state; one row per tenant+pipeline (matches the orchestrator
 * key).
 */
@Setter
@Getter
@Entity
@Table(name = "sync_jobs")
public class SyncJobEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId = "00000000-0000-0000-0000-000000000000";

    @Column(name = "pipeline_id", nullable = false, length = 36)
    private String pipelineId;

    @Column(nullable = false, length = 20)
    private String state;

    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String statistics;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public SyncJobEntity() {
    }
}
