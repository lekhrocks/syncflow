package com.syncflow.api.cdc.entity;

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
@Table(name = "cdc_offsets")
public class CdcOffsetEntity {

    @Id
    @Column(name = "pipeline_id", length = 36)
    private String pipelineId;

    @Column(name = "connector_type", nullable = false, length = 20)
    private String connectorType;

    /**
     * JSONB column storing the offset map (e.g. {"lsn":"0/1A2B3C4"} for Postgres).
     */
    @Column(name = "offset_data", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String offsetData;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CdcOffsetEntity() {
    }

}
