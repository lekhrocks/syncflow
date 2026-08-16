package com.syncflow.tenant;

import java.time.Instant;
import java.util.Set;

public record TenantContext(
        TenantId tenantId,
        OrganizationId organizationId,
        WorkspaceId workspaceId,
        ProjectId projectId,
        String userId,
        Set<String> roles,
        Instant establishedAt) {

    /**
     * Construct a system-tenant context for background workers / schedulers that
     * do not have a user request behind them. Use this instead of a null context
     * so worker code can dereference {@code tenantContext.tenantId()} safely.
     */
    public static TenantContext system() {
        return new TenantContext(TenantId.DEFAULT, null, null, null,
                "system", Set.of(), Instant.now());
    }

    /** A system-tenant context that acts on behalf of a specific tenant. */
    public static TenantContext system(TenantId tenantId) {
        return new TenantContext(tenantId != null ? tenantId : TenantId.DEFAULT,
                null, null, null, "system", Set.of(), Instant.now());
    }

    /**
     * Defensive non-null guard for method parameters. Returns the context
     * unchanged when non-null, otherwise throws. Use at orchestrator entry
     * points so a missing tenant never produces an NPE inside a worker.
     */
    public static TenantContext require(TenantContext ctx) {
        if (ctx == null) {
            throw new IllegalStateException(
                    "TenantContext is required; pass the request tenant or TenantContext.system()");
        }
        return ctx;
    }
}
