package com.syncflow.persistence.sync.repository;

import com.syncflow.persistence.sync.entity.EventQueueSnapshotEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persist and recover event queue snapshots for multi-region failover.
 *
 * <p>
 * Snapshots survive pod crashes and are rehydrated into the event queue on
 * startup.
 */
public interface EventQueueSnapshotRepository extends JpaRepository<EventQueueSnapshotEntity, Long> {

    /**
     * Find most recent snapshot for a pipeline (FIFO recovery).
     *
     * <p>
     * Used on startup to rehydrate lost events.
     *
     * @param tenantId
     *            tenant owning the pipeline
     * @param pipelineId
     *            pipeline id
     * @return most recent snapshot, or empty if none
     */
    @Query("SELECT s FROM EventQueueSnapshotEntity s WHERE s.tenantId = :tenantId AND s.pipelineId = :pipelineId ORDER BY s.createdAt DESC LIMIT 1")
    Optional<EventQueueSnapshotEntity> findMostRecent(
            @Param("tenantId") String tenantId, @Param("pipelineId") String pipelineId);

    /**
     * Find all snapshots for a tenant (for cleanup / debugging).
     *
     * @param tenantId
     *            tenant id
     * @return all snapshots
     */
    List<EventQueueSnapshotEntity> findByTenantId(String tenantId);

    /**
     * Find all snapshots for a tenant and pipeline.
     *
     * @param tenantId
     *            tenant id
     * @param pipelineId
     *            pipeline id
     * @return all snapshots (usually 0-1 recent ones)
     */
    List<EventQueueSnapshotEntity> findByTenantIdAndPipelineId(String tenantId, String pipelineId);

    /**
     * Delete expired snapshots (TTL cleanup).
     *
     * @param now
     *            current instant
     * @return number of deleted rows
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM EventQueueSnapshotEntity WHERE expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);

    /**
     * Delete all snapshots for a pipeline (when sync completes successfully).
     *
     * @param tenantId
     *            tenant id
     * @param pipelineId
     *            pipeline id
     * @return number of deleted rows
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM EventQueueSnapshotEntity WHERE tenantId = :tenantId AND pipelineId = :pipelineId")
    int deleteByTenantIdAndPipelineId(
            @Param("tenantId") String tenantId, @Param("pipelineId") String pipelineId);
}
