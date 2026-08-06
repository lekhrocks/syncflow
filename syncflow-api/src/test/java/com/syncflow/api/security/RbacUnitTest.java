package com.syncflow.api.security;

import com.syncflow.api.security.rbac.AuthorizationService;
import com.syncflow.api.security.rbac.PolicyResolver;
import com.syncflow.api.security.rbac.ResourcePermission;
import com.syncflow.tenant.TenantContext;
import com.syncflow.tenant.TenantContextHolder;
import com.syncflow.tenant.TenantId;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RbacUnitTest {

    private final PolicyResolver resolver = new PolicyResolver();
    private final AuthorizationService authz = new AuthorizationService(resolver);

    @Test
    void defaultPermissionsIncludeRead() {
        var ctx = new TenantContext(TenantId.DEFAULT, null, null, null, "user", Set.of(), java.time.Instant.now());
        assertTrue(authz.isPermitted(ResourcePermission.PIPELINE_READ, ctx));
        assertTrue(authz.isPermitted(ResourcePermission.CONNECTION_READ, ctx));
        assertTrue(authz.isPermitted(ResourcePermission.METRICS_READ, ctx));
    }

    @Test
    void defaultPermissionsExcludeWrite() {
        var ctx = new TenantContext(TenantId.DEFAULT, null, null, null, "user", Set.of(), java.time.Instant.now());
        assertFalse(authz.isPermitted(ResourcePermission.PIPELINE_DELETE, ctx));
        assertFalse(authz.isPermitted(ResourcePermission.CONNECTION_DELETE, ctx));
    }

    @Test
    void adminHasAllPermissions() {
        var ctx = new TenantContext(TenantId.DEFAULT, null, null, null, "admin", Set.of(), java.time.Instant.now());
        assertTrue(authz.isPermitted(ResourcePermission.AUDIT_READ, ctx));
        assertTrue(authz.isPermitted(ResourcePermission.AI_USE, ctx));
    }

    @Test
    void adminRoleGrantsAllPermissions() {
        // A non-admin username with the ADMIN role gets full permissions.
        var ctx = new TenantContext(TenantId.DEFAULT, null, null, null, "alice",
                Set.of(PolicyResolver.ADMIN_ROLE), java.time.Instant.now());
        assertTrue(authz.isPermitted(ResourcePermission.AUDIT_READ, ctx));
        assertTrue(authz.isPermitted(ResourcePermission.AI_USE, ctx));
        assertTrue(authz.isPermitted(ResourcePermission.ORG_WRITE, ctx));
    }

    @Test
    void tenantContextHolderRoundTrip() {
        var ctx = new TenantContext(TenantId.DEFAULT, null, null, null, "user", Set.of(), java.time.Instant.now());
        TenantContextHolder.set(ctx);
        assertEquals("user", TenantContextHolder.get().userId());
        assertEquals(TenantId.DEFAULT, TenantContextHolder.getTenantId());
        TenantContextHolder.clear();
        assertNull(TenantContextHolder.get());
    }

    @Test
    void tenantContextDefaultWhenNoContext() {
        TenantContextHolder.clear();
        assertEquals(TenantId.DEFAULT, TenantContextHolder.getTenantId());
    }

    @Test
    void resourcePermissionViewerSet() {
        var perms = ResourcePermission.viewer();
        assertTrue(perms.contains(ResourcePermission.PIPELINE_READ));
        assertTrue(perms.contains(ResourcePermission.METRICS_READ));
        assertFalse(perms.contains(ResourcePermission.PIPELINE_WRITE));
    }

    @Test
    void resourcePermissionDeveloperSet() {
        var perms = ResourcePermission.developer();
        assertTrue(perms.contains(ResourcePermission.PIPELINE_WRITE));
        assertTrue(perms.contains(ResourcePermission.CONNECTION_WRITE));
        assertTrue(perms.contains(ResourcePermission.AI_USE));
    }

    @Test
    void resourcePermissionWorkspaceAdminSet() {
        var perms = ResourcePermission.workspaceAdmin();
        assertTrue(perms.containsAll(ResourcePermission.viewer()));
    }
}
