package com.syncflow.api.connection.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Setter
@Getter
@Entity
@Table(name = "connections")
public class ConnectionEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId = "00000000-0000-0000-0000-000000000000";

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "connection_type", nullable = false, length = 20)
    private String connectionType;

    @Column(nullable = false, length = 255)
    private String host;

    @Column(nullable = false)
    private int port;

    @Column(nullable = false, length = 255)
    private String database;

    @Column(name = "options_", columnDefinition = "TEXT")
    private String options;

    @Column(name = "encrypted_username", nullable = false, length = 1024)
    private String encryptedUsername;

    @Column(name = "encrypted_password", nullable = false, length = 1024)
    private String encryptedPassword;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "db_version", length = 50)
    private String dbVersion;

    @Column(name = "driver_name", length = 50)
    private String driverName;

    @Column(name = "last_latency_ms")
    private long lastLatencyMs;

    @Column(name = "last_checked")
    private Instant lastChecked;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ConnectionEntity() {
    }

}
