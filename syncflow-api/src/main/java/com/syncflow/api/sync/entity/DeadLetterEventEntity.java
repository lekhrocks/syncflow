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

@Setter
@Getter
@Entity
@Table(name = "dead_letter_events")
public class DeadLetterEventEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId = "00000000-0000-0000-0000-000000000000";

    @Column(name = "pipeline_id", nullable = false, length = 36)
    private String pipelineId;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    /** Full CDCEvent serialized as JSONB. */
    @Column(name = "event_data", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String eventData;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "failure_type", nullable = false, length = 20)
    private String failureType;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "replayed_at")
    private Instant replayedAt;

    @Column(name = "replay_count", nullable = false)
    private int replayCount;

    public DeadLetterEventEntity() {
    }

}
