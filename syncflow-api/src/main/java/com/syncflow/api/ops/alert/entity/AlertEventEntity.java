package com.syncflow.api.ops.alert.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Ops alert; live incidents surfaced on the dashboard. */
@Setter
@Getter
@Entity
@Table(name = "alert_events")
public class AlertEventEntity {

    @Id
    @Column(length = 50)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId = "00000000-0000-0000-0000-000000000000";

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(length = 255)
    private String source;

    @Column(name = "pipeline_id", length = 36)
    private String pipelineId;

    @Column(name = "connection_id", length = 36)
    private String connectionId;

    @Column(name = "event_time", nullable = false)
    private Instant eventTime;

    @Column(nullable = false)
    private boolean acknowledged;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public AlertEventEntity() {
    }
}
