package com.syncflow.api.security.apikey;

import com.syncflow.tenant.TenantId;
import java.time.Instant;
import java.util.UUID;

public record ApiKey(
        UUID id,
        TenantId tenantId,
        String hashedKey,
        String prefix,
        String label,
        String scope,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt) {

    public boolean isActive() {
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(Instant.now()));
    }
}
