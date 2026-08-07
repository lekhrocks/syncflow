package com.syncflow.tenant;

import java.time.Instant;
import java.util.Set;

/**
 * Shared tenant helpers: the current request tenant, and the construction of a
 * worker-thread tenant context (virtual threads do not inherit the request
 * ThreadLocal). Centralizes the otherwise-repeated
 * {@code TenantContextHolder.getTenantId().value()} accessor.
 */
public final class TenantSupport {

    /** Identity used by background/system workers (not a real user account). */
    public static final String SYSTEM_USER = "system";

    private TenantSupport() {
    }

    /** Current request tenant id, or the single-tenant default when unset. */
    public static String tenantId() {
        return TenantContextHolder.getTenantId().value();
    }

    /** A tenant context for a background worker, carrying a system identity. */
    public static TenantContext workerContext(TenantId tenantId) {
        return new TenantContext(tenantId, null, null, null, SYSTEM_USER, Set.of(), Instant.now());
    }
}
