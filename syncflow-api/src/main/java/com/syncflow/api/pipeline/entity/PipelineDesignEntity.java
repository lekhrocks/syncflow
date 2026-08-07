package com.syncflow.api.pipeline.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@Entity
@Table(name = "pipeline_designs")
public class PipelineDesignEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId = "00000000-0000-0000-0000-000000000000";

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String source;

    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String destination;

    @Column(name = "table_mappings", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String tableMappings;

    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String settings;

    @Column(nullable = false)
    private int version;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "pipeline", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("version ASC")
    private List<PipelineDesignVersionEntity> versions = new ArrayList<>();

    public PipelineDesignEntity() {
    }

}
