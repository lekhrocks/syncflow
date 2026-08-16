package com.syncflow.api.sync.repository;

import com.syncflow.api.sync.entity.ProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity, String> {

    boolean existsByEventId(String eventId);

    void deleteByPipelineId(String pipelineId);

    /** Purge entries older than the given cutoff (called by scheduled cleanup). */
    @Modifying
    @Query("DELETE FROM ProcessedEventEntity e WHERE e.processedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

    /**
     * Atomically insert if absent. Returns 1 if inserted (this call won), 0 if
     * the row already existed (another worker beat us). Backed by a unique
     * constraint on event_id; a concurrent INSERT that violates the constraint
     * is silently absorbed and returns 0.
     */
    @Modifying
    @Query(value = """
            INSERT INTO processed_events (event_id, pipeline_id, processed_at)
            VALUES (:eventId, :pipelineId, :processedAt)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") String eventId,
            @Param("pipelineId") String pipelineId,
            @Param("processedAt") Instant processedAt);
}
