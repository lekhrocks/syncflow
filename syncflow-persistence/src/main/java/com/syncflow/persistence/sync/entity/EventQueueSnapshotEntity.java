package com.syncflow.persistence.sync.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Persists pending CDC events from SyncOrchestrator.eventQueues to survive pod
 * crashes.
 *
 * <p>
 * Snapshot is taken:
 * - On @PreDestroy (graceful shutdown)
 * - On significant failure (exception in sync worker)
 *
 * <p>
 * Rehydrated on startup by SyncOrchestrator.rehydrateFromDatabase(). Events are
 * re-queued in original order (FIFO).
 *
 * <p>
 * TTL: Snapshot is deleted after 24h or on successful sync completion,
 * whichever comes first.
 * This prevents snapshot accumulation if pod crashes repeatedly.
 */
@Entity
@Table(name = "event_queue_snapshots")
public class EventQueueSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tenant ID for multi-tenancy isolation. */
    @Column(nullable = false)
    private String tenantId;

    /** Pipeline ID to which these events belong. */
    @Column(nullable = false)
    private String pipelineId;

    /**
     * Number of events in the snapshot (for quick size check without
     * deserializing).
     */
    @Column(nullable = false)
    private int eventCount;

    /**
     * Serialized CDCEvent objects (JSON array). Persisted as JSONB in PostgreSQL
     * for efficient
     * querying and filtering.
     *
     * <p>
     * Format: [{"operation":"INSERT","table":"users",...}, ...]
     */
    @Column(nullable = false, columnDefinition = "jsonb")
    private String eventsJson;

    /** When this snapshot was created (pod crash or graceful shutdown). */
    @Column(nullable = false)
    private Instant createdAt;

    /** When this snapshot was created (pod crash or graceful shutdown). */
    @Column(nullable = false)
    private Instant expiresAt;

    /** Pod name that created this snapshot (for debugging). */
    @Column
    private String podName;

    /**
     * Reason for snapshot (e.g., "graceful_shutdown", "pod_crash",
     * "explicit_save").
     */
    @Column
    private String reason;

    // Constructors

    public EventQueueSnapshotEntity() {
    }

    public EventQueueSnapshotEntity(
            String tenantId,
            String pipelineId,
            int eventCount,
            String eventsJson,
            String podName,
            String reason) {
        this.tenantId = tenantId;
        this.pipelineId = pipelineId;
        this.eventCount = eventCount;
        this.eventsJson = eventsJson;
        this.podName = podName;
        this.reason = reason;
        this.createdAt = Instant.now();
        // 24 hour TTL
        this.expiresAt = Instant.now().plusSeconds(86400);
    }

    // Getters / Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getPipelineId() {
        return pipelineId;
    }

    public void setPipelineId(String pipelineId) {
        this.pipelineId = pipelineId;
    }

    public int getEventCount() {
        return eventCount;
    }

    public void setEventCount(int eventCount) {
        this.eventCount = eventCount;
    }

    public String getEventsJson() {
        return eventsJson;
    }

    public void setEventsJson(String eventsJson) {
        this.eventsJson = eventsJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getPodName() {
        return podName;
    }

    public void setPodName(String podName) {
        this.podName = podName;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
