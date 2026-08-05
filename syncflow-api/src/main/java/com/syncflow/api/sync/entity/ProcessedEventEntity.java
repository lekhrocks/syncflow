package com.syncflow.api.sync.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Setter
@Getter
@Entity
@Table(name = "processed_events")
public class ProcessedEventEntity {

    @Id
    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "pipeline_id", nullable = false, length = 36)
    private String pipelineId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public ProcessedEventEntity() {
    }

    public ProcessedEventEntity(String eventId, String pipelineId, Instant processedAt) {
        this.eventId = eventId;
        this.pipelineId = pipelineId;
        this.processedAt = processedAt;
    }

}
