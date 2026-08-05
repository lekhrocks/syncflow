package com.syncflow.api.sync.repository;

import com.syncflow.api.sync.entity.DeadLetterEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEventEntity, String> {

    List<DeadLetterEventEntity> findByPipelineIdOrderByCreatedAtDesc(String pipelineId);

    @Modifying
    @Query("UPDATE DeadLetterEventEntity e SET e.replayedAt = CURRENT_TIMESTAMP, e.replayCount = e.replayCount + 1 WHERE e.id = :id")
    void markReplayed(@Param("id") String id);

    long countByPipelineId(String pipelineId);

    void deleteByPipelineId(String pipelineId);
}
