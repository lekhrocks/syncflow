package com.syncflow.api.security.audit;

import com.syncflow.tenant.TenantId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EnterpriseAuditStore {

    private final Map<UUID, EnterpriseAuditRecord> store = new ConcurrentHashMap<>();

    public EnterpriseAuditRecord record(TenantId tenantId, String actor, String action,
            String resourceType, String resourceId,
            String details, String ipAddress) {
        var id = UUID.randomUUID();
        var record = new EnterpriseAuditRecord(id, tenantId, actor, action,
                resourceType, resourceId, details, ipAddress, false, Instant.now());
        store.put(id, record);
        return record;
    }

    public List<EnterpriseAuditRecord> list(TenantId tenantId, int limit) {
        return store.values().stream()
                .filter(r -> r.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(EnterpriseAuditRecord::timestamp).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    public boolean hardDelete() {
        return store.isEmpty();
    }

    /** Compliance: GDPR right-to-delete — remove all records for a tenant. */
    public void anonymize(UserDeletionRequest req) {
        store.values().removeIf(r -> r.tenantId().equals(req.tenantId()));
    }

    public record UserDeletionRequest(TenantId tenantId) {
    }
}
