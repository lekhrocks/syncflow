package com.syncflow.api.snapshot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Resume checkpoint for a snapshot pipeline+table; one row per tenant. */
@Setter
@Getter
@Entity
@Table(name = "snapshot_checkpoints")
public class SnapshotCheckpointEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId = "00000000-0000-0000-0000-000000000000";

    @Column(name = "pipeline_id", nullable = false, length = 36)
    private String pipelineId;

    @Column(name = "source_table", nullable = false, length = 255)
    private String sourceTable;

    @Column(name = "last_batch_number", nullable = false)
    private int lastBatchNumber;

    @Column(name = "rows_processed", nullable = false)
    private long rowsProcessed;

    @Column(name = "cursor_pos", length = 4096)
    private String cursorPos;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public SnapshotCheckpointEntity() {
    }
}
