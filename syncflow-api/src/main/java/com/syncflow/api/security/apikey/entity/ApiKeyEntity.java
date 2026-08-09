package com.syncflow.api.security.apikey.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** API key; hashed value unique, revoke/expiry drive isActive(). */
@Setter
@Getter
@Entity
@Table(name = "api_keys")
public class ApiKeyEntity {

    @Id
    @Column(length = 36)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId = "00000000-0000-0000-0000-000000000000";

    @Column(name = "hashed_key", nullable = false, length = 64)
    private String hashedKey;

    @Column(length = 16)
    private String prefix;

    @Column(length = 255)
    private String label;

    @Column(length = 50)
    private String scope;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public ApiKeyEntity() {
    }
}
