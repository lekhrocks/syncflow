package com.syncflow.api.security;

import com.syncflow.tenant.OrganizationId;
import com.syncflow.tenant.ProjectId;
import com.syncflow.tenant.TenantContext;
import com.syncflow.tenant.TenantContextHolder;
import com.syncflow.tenant.TenantId;
import com.syncflow.tenant.WorkspaceId;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantIsolationTest {

    @Test
    void tenantIdEquality() {
        assertEquals(TenantId.from("abc"), TenantId.from("abc"));
        assertNotEquals(TenantId.from("abc"), TenantId.from("xyz"));
    }

    @Test
    void tenantIdRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new TenantId(""));
        assertThrows(IllegalArgumentException.class, () -> new TenantId("  "));
        assertThrows(IllegalArgumentException.class, () -> new TenantId(null));
    }

    @Test
    void tenantContextPreservesRoles() {
        var roles = Set.of("VIEWER", "OPERATOR");
        var ctx = new TenantContext(TenantId.generate(), null, null, null, "user1", roles, java.time.Instant.now());
        assertTrue(ctx.roles().contains("VIEWER"));
        assertEquals(2, ctx.roles().size());
    }

    @Test
    void organizationIdRoundTrip() {
        var id = OrganizationId.generate();
        assertNotNull(id);
        assertEquals(id, OrganizationId.from(id.value()));
    }

    @Test
    void workspaceIdRoundTrip() {
        var id = WorkspaceId.generate();
        assertNotNull(id);
        assertEquals(id, WorkspaceId.from(id.value()));
    }

    @Test
    void projectIdRoundTrip() {
        var id = ProjectId.generate();
        assertNotNull(id);
        assertEquals(id, ProjectId.from(id.value()));
    }

    @Test
    void tenantContextCrossTenantIsolation() {
        var tenantA = TenantId.generate();
        var tenantB = TenantId.generate();
        assertNotEquals(tenantA, tenantB);

        var ctxA = new TenantContext(tenantA, null, null, null, "userA", Set.of(), java.time.Instant.now());
        var ctxB = new TenantContext(tenantB, null, null, null, "userB", Set.of(), java.time.Instant.now());
        assertNotEquals(ctxA.tenantId(), ctxB.tenantId());
        assertNotEquals(ctxA.userId(), ctxB.userId());
    }

    @Test
    void multipleTenantContextsThreadSafe() throws Exception {
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
        for (int i = 0; i < 10; i++) {
            assertEquals(TenantId.from("t" + i), results.get("t" + i));
        }
    }
}
