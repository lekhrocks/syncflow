package com.syncflow.api.connection.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "connections")
public class ConnectionEntity {

    @Id
    @Column(length = 36)
    private String id;

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

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getConnectionType() {
        return connectionType;
    }
    public void setConnectionType(String connectionType) {
        this.connectionType = connectionType;
    }

    public String getHost() {
        return host;
    }
    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }
    public void setPort(int port) {
        this.port = port;
    }

    public String getDatabase() {
        return database;
    }
    public void setDatabase(String database) {
        this.database = database;
    }

    public String getOptions() {
        return options;
    }
    public void setOptions(String options) {
        this.options = options;
    }

    public String getEncryptedUsername() {
        return encryptedUsername;
    }
    public void setEncryptedUsername(String encryptedUsername) {
        this.encryptedUsername = encryptedUsername;
    }

    public String getEncryptedPassword() {
        return encryptedPassword;
    }
    public void setEncryptedPassword(String encryptedPassword) {
        this.encryptedPassword = encryptedPassword;
    }

    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }

    public String getDbVersion() {
        return dbVersion;
    }
    public void setDbVersion(String dbVersion) {
        this.dbVersion = dbVersion;
    }

    public String getDriverName() {
        return driverName;
    }
    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    public long getLastLatencyMs() {
        return lastLatencyMs;
    }
    public void setLastLatencyMs(long lastLatencyMs) {
        this.lastLatencyMs = lastLatencyMs;
    }

    public Instant getLastChecked() {
        return lastChecked;
    }
    public void setLastChecked(Instant lastChecked) {
        this.lastChecked = lastChecked;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
