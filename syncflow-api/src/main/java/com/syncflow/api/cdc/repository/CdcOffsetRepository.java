package com.syncflow.api.cdc.repository;

import com.syncflow.api.cdc.entity.CdcOffsetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CdcOffsetRepository extends JpaRepository<CdcOffsetEntity, String> {

    /**
     * Upsert offset: insert on first save, update on subsequent saves.
     * Uses PostgreSQL ON CONFLICT DO UPDATE to be atomic.
     */
    @Modifying
    @Query(value = """
            INSERT INTO cdc_offsets (pipeline_id, connector_type, offset_data, saved_at, updated_at)
            VALUES (:pipelineId, :connectorType, CAST(:offsetData AS jsonb), NOW(), NOW())
            ON CONFLICT (pipeline_id)
            DO UPDATE SET
                connector_type = EXCLUDED.connector_type,
                offset_data    = EXCLUDED.offset_data,
                updated_at     = NOW()
            """, nativeQuery = true)
    void upsert(@Param("pipelineId") String pipelineId,
            @Param("connectorType") String connectorType,
            @Param("offsetData") String offsetData);
}
