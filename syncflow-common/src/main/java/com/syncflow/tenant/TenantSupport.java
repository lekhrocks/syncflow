package com.syncflow.tenant;

/**
 * Shared tenant helpers: the current request tenant, and the construction of a
 * worker-thread tenant context (virtual threads do not inherit the request
 * ThreadLocal). Centralizes the otherwise-repeated
 * {@code TenantContextHolder.getTenantId().value()} accessor.
 * <p>
 * Note: this is a legacy helper. New code should pass {@link TenantContext}
 * explicitly through method parameters rather than relying on ThreadLocal.
 */
public final class TenantSupport {

    /** Identity used by background/system workers (not a real user account). */
    public static final String SYSTEM_USER = "system";

    private TenantSupport() {
    }

    /**
     * Current request tenant id, or the single-tenant default when unset.
     *
     * @deprecated Prefer passing {@link TenantContext} explicitly through method
     *             parameters. This method reads from a ThreadLocal, which is
     *             unavailable on virtual-thread worker paths and returns a silent
     *             default instead of failing. Existing callers in controllers are
     *             acceptable (request thread); worker-thread callers are bugs.
     */
    @Deprecated
    public static String tenantId() {
        return TenantContextHolder.getTenantId().value();
    }

    /** A tenant context for a background worker, carrying a system identity. */
    public static TenantContext workerContext(TenantId tenantId) {
        return TenantContext.system(tenantId);
    }
}
