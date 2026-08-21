package com.syncflow.persistence.workflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Workflow instance runtime state; task graph and executions as JSONB. */
@Setter
@Getter
@Entity
@Table(name = "workflow_instances")
public class WorkflowInstanceEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId = "00000000-0000-0000-0000-000000000000";

    @Column(name = "pipeline_id", nullable = false, length = 36)
    private String pipelineId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String tasks;

    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String executions;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public WorkflowInstanceEntity() {
    }
}
