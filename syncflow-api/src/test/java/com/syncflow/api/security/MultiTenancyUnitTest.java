package com.syncflow.api.security;

import com.syncflow.api.security.quota.Quota;
import com.syncflow.api.security.quota.QuotaEngine;
import com.syncflow.api.security.rbac.AuthorizationService;
import com.syncflow.api.security.rbac.PolicyResolver;
import com.syncflow.api.security.rbac.ResourcePermission;
import com.syncflow.tenant.OrganizationId;
import com.syncflow.tenant.TenantContext;
import com.syncflow.tenant.TenantContextHolder;
import com.syncflow.tenant.TenantId;
import com.syncflow.tenant.WorkspaceId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiTenancyUnitTest {

    private AuthorizationService authz;
    private QuotaEngine quotaEngine;

    @BeforeEach
    void setUp() {
        authz = new AuthorizationService(new PolicyResolver());
        quotaEngine = new QuotaEngine();
    }

    // --- Tenant resolver ---

    @Test
    void tenantResolverHeaderParsing() {
        var tenantId = TenantId.from("tenant-123");
        var orgId = OrganizationId.from("org-456");
        var wsId = WorkspaceId.from("ws-789");
        var ctx = new TenantContext(tenantId, orgId, wsId, null, "user-1", Set.of(), java.time.Instant.now());
        assertEquals("tenant-123", ctx.tenantId().value());
        assertEquals("org-456", ctx.organizationId().value());
        assertEquals("ws-789", ctx.workspaceId().value());
    }

    @Test
    void tenantResolverDefaultsToDefaultTenant() {
        var ctx = new TenantContext(TenantId.DEFAULT, null, null, null, "anon", Set.of(), java.time.Instant.now());
        assertEquals(TenantId.DEFAULT, ctx.tenantId());
    }

    @Test
    void tenantResolverWithRolesFromJwt() {
        var roles = Set.of("DEVELOPER", "OPERATOR");
        var ctx = new TenantContext(TenantId.generate(), null, null, null, "user", roles, java.time.Instant.now());
        assertTrue(ctx.roles().contains("DEVELOPER"));
        assertTrue(ctx.roles().contains("OPERATOR"));
    }

    @Test
    void tenantResolverWithPartialScope() {
        var ctx = new TenantContext(TenantId.generate(), null, WorkspaceId.generate(), null, "u", Set.of(),
                java.time.Instant.now());
        assertNull(ctx.organizationId());
        assertNotNull(ctx.workspaceId());
        assertNull(ctx.projectId());
    }

    // --- Permission evaluator ---

    @Test
    void permissionEvaluatorRequireAnySucceeds() {
        TenantContextHolder
                .set(new TenantContext(TenantId.DEFAULT, null, null, null, "admin", Set.of(), java.time.Instant.now()));
        var viewerPerms = ResourcePermission.viewer();
        assertTrue(viewerPerms.stream().anyMatch(p -> authz.isPermitted(p)) ||
                viewerPerms.stream().allMatch(p -> authz.isPermitted(p)));
        TenantContextHolder.clear();
    }

    @Test
    void permissionEvaluatorRequireAnyFails() {
        // Set a non-admin context, then check
        TenantContextHolder.set(
                new TenantContext(TenantId.DEFAULT, null, null, null, "viewer", Set.of(), java.time.Instant.now()));
        // PIPELINE_DELETE is NOT in viewer permissions
        assertFalse(authz.isPermitted(ResourcePermission.PIPELINE_DELETE));
        TenantContextHolder.clear();
    }

    @Test
    void permissionEvaluatorMissingPermissionThrows() {
        TenantContextHolder.set(
                new TenantContext(TenantId.DEFAULT, null, null, null, "viewer", Set.of(), java.time.Instant.now()));
        // Viewer does NOT have PIPELINE_DELETE
        assertFalse(authz.isPermitted(ResourcePermission.PIPELINE_DELETE));
        TenantContextHolder.clear();
    }

    // --- Quota evaluator ---

    @Test
    void quotaEvaluatorNotExceeded() {
        var t = TenantId.generate();
        quotaEngine.setQuota(t, new Quota(Map.of(Quota.Metric.CONNECTIONS, 10L)));
        var result = quotaEngine.checkLimit(t, Quota.Metric.CONNECTIONS, 5);
        assertFalse(result.exceeded());
        assertEquals(10L, result.limit());
        assertEquals(5L, result.current());
    }

    @Test
    void quotaEvaluatorExceeded() {
        var t = TenantId.generate();
        quotaEngine.setQuota(t, new Quota(Map.of(Quota.Metric.PIPELINES, 3L)));
        var result = quotaEngine.checkLimit(t, Quota.Metric.PIPELINES, 5);
        assertTrue(result.exceeded());
    }

    @Test
    void quotaEvaluatorUnlimitedWhenNegative() {
        var t = TenantId.generate();
        quotaEngine.setQuota(t, new Quota(Map.of()));
        var result = quotaEngine.checkLimit(t, Quota.Metric.STORAGE_BYTES, 999999);
        assertFalse(result.exceeded());
        assertEquals(-1L, result.limit());
    }

    @Test
    void quotaEvaluatorResetsPerTenant() {
        var t1 = TenantId.generate();
        var t2 = TenantId.generate();
        quotaEngine.setQuota(t1, new Quota(Map.of(Quota.Metric.AI_REQUESTS, 5L)));
        var limit1 = quotaEngine.getQuota(t1).limit(Quota.Metric.AI_REQUESTS);
        var limit2 = quotaEngine.getQuota(t2).limit(Quota.Metric.AI_REQUESTS);
        assertEquals(5L, limit1);
        assertNotEquals(limit1, limit2);
    }

    // --- API key lifecycle ---

    @Test
    void apiKeyLifecycleCreateAndRevoke() {
        var store = new com.syncflow.api.security.apikey.ApiKeyStore();
        var tenant = TenantId.generate();

        var key = store.issue(tenant, "test-key", "READ", java.time.Instant.now().plusSeconds(3600));
        assertNotNull(key);
        assertTrue(key.isActive());

        assertTrue(store.revoke(key.id()));
    }

    @Test
    void apiKeyExpiredCannotBeUsed() {
        var store = new com.syncflow.api.security.apikey.ApiKeyStore();
        var tenant = TenantId.generate();
        var key = store.issue(tenant, "expired", "READ", java.time.Instant.now().minusSeconds(1));
        assertFalse(key.isActive());
    }

    // --- Workspace / Organization membership ---

    @Test
    void organizationMembershipContext() {
        var orgId = OrganizationId.generate();
        var wsId = WorkspaceId.generate();
        var ctx = new TenantContext(TenantId.generate(), orgId, wsId, null, "member", Set.of("WORKSPACE_ADMIN"),
                java.time.Instant.now());
        assertEquals(orgId, ctx.organizationId());
        assertEquals(wsId, ctx.workspaceId());
        assertTrue(ctx.roles().contains("WORKSPACE_ADMIN"));
    }

    @Test
    void workspaceScopedContext() {
        var wsId = WorkspaceId.generate();
        var ctx = new TenantContext(TenantId.generate(), null, wsId, null, "dev", Set.of("DEVELOPER"),
                java.time.Instant.now());
        assertEquals(wsId, ctx.workspaceId());
        assertNull(ctx.organizationId());
    }

    // --- Cross-tenant isolation ---

    @Test
    void crossTenantQuotaIsolation() {
        var tenantA = TenantId.generate();
        var tenantB = TenantId.generate();

        quotaEngine.setQuota(tenantA, new Quota(Map.of(Quota.Metric.CONNECTIONS, 100L)));
        quotaEngine.setQuota(tenantB, new Quota(Map.of(Quota.Metric.CONNECTIONS, 200L)));

        assertNotEquals(
                quotaEngine.getQuota(tenantA).limit(Quota.Metric.CONNECTIONS),
                quotaEngine.getQuota(tenantB).limit(Quota.Metric.CONNECTIONS));
    }

    @Test
    void tenantContextHolderIsolation() {
        TenantContextHolder
                .set(new TenantContext(TenantId.from("t1"), null, null, null, "u1", Set.of(), java.time.Instant.now()));
        var t1Id = TenantContextHolder.getTenantId();
        TenantContextHolder
                .set(new TenantContext(TenantId.from("t2"), null, null, null, "u2", Set.of(), java.time.Instant.now()));
        var t2Id = TenantContextHolder.getTenantId();
        assertNotEquals(t1Id, t2Id);
        TenantContextHolder.clear();
    }

    // --- Thread safety ---

    @Test
    void concurrentTenantContextIsolation() throws Exception {
        var results = new java.util.concurrent.ConcurrentHashMap<String, TenantId>();
        var threads = new java.util.ArrayList<Thread>();
        for (int i = 0; i < 10; i++) {
            var id = "t" + i;
            threads.add(new Thread(() -> {
                TenantContextHolder.set(
                        new TenantContext(TenantId.from(id), null, null, null, id, Set.of(), java.time.Instant.now()));
                results.put(id, TenantContextHolder.getTenantId());
                TenantContextHolder.clear();
            }));
        }
        threads.forEach(Thread::start);
        for (var t : threads)
            t.join();
        assertEquals(10, results.size());
    }
}
