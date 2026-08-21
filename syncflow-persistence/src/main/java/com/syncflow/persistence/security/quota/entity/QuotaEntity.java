package com.syncflow.persistence.security.quota.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Per-tenant quota row; limits map as JSONB. */
@Setter
@Getter
@Entity
@Table(name = "quotas")
public class QuotaEntity {

    @Id
    @Column(name = "tenant_id", length = 36)
    private String tenantId;

    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String limits;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public QuotaEntity() {
    }
}
