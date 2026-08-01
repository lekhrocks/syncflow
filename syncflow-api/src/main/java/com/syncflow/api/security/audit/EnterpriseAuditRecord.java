package com.syncflow.api.security.audit;

import com.syncflow.tenant.TenantId;
import java.time.Instant;
import java.util.UUID;

public record EnterpriseAuditRecord(
        UUID id,
        TenantId tenantId,
        String actor,
        String action,
        String resourceType,
        String resourceId,
        String details,
        String ipAddress,
        boolean suspicious,
        Instant timestamp) {
}
