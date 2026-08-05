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
}
