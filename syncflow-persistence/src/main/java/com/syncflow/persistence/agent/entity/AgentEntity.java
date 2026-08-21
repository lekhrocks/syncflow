package com.syncflow.persistence.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Fleet agent; capabilities, labels, hardware metrics as JSONB. */
@Setter
@Getter
@Entity
@Table(name = "agents")
public class AgentEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId = "00000000-0000-0000-0000-000000000000";

    @Column(length = 50)
    private String version;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String capabilities;

    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String labels;

    @Column(length = 50)
    private String environment;

    @Column(length = 50)
    private String region;

    @Column(length = 255)
    private String hostname;

    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String hardware;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "last_heartbeat", nullable = false)
    private Instant lastHeartbeat;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AgentEntity() {
    }
}
