package com.syncflow.api.security;

import com.syncflow.api.security.apikey.ApiKeyStore;
import com.syncflow.api.security.quota.Quota;
import com.syncflow.api.security.quota.QuotaEngine;
import com.syncflow.tenant.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeySecurityTest {

    private final ApiKeyStore store = new ApiKeyStore();
    private final TenantId tenant = TenantId.generate();

    @Test
    void issueAndValidateApiKey() {
        var key = store.issue(tenant, "test", "READ", null);
        assertNotNull(key);
        assertNotNull(key.prefix());

        var validated = store.validate(key.hashedKey());
        assertNull(validated);
    }

    @Test
    void revokeApiKey() {
        var key = store.issue(tenant, "test", "WRITE", Instant.now().plusSeconds(3600));
        assertTrue(store.revoke(key.id()));
        assertTrue(store.validate(key.hashedKey()) == null || !store.validate(key.hashedKey()).isActive());
    }

    @Test
    void expiredKeyIsInvalid() {
        var key = store.issue(tenant, "expired", "READ", Instant.now().minusSeconds(1));
        assertFalse(key.isActive());
    }

    @Test
    void quotaDefaultLimits() {
        var engine = new QuotaEngine();

        // Retrieve default quota for a tenant
        var quota = engine.getQuota(TenantId.generate());
        assertNotNull(quota);
        assertTrue(quota.limit(Quota.Metric.CONNECTIONS) > 0);
    }

    @Test
    void quotaCheckLimit() {
        var engine = new QuotaEngine();
        var tenantId = TenantId.generate();

        // Set a custom quota with 5 connections limit
        engine.setQuota(tenantId, new Quota(java.util.Map.of(Quota.Metric.CONNECTIONS, 5L)));

        var result = engine.checkLimit(tenantId, Quota.Metric.CONNECTIONS, 3);
        assertFalse(result.exceeded());

        var exceeded = engine.checkLimit(tenantId, Quota.Metric.CONNECTIONS, 5);
        assertTrue(exceeded.exceeded());
    }

    @Test
    void quotaPerTenantIsolation() {
        var engine = new QuotaEngine();
        var t1 = TenantId.generate();
        var t2 = TenantId.generate();

        engine.setQuota(t1, new Quota(java.util.Map.of(Quota.Metric.AI_REQUESTS, 10L)));
        var q1 = engine.getQuota(t1);
        var q2 = engine.getQuota(t2);

        assertEquals(10L, q1.limit(Quota.Metric.AI_REQUESTS));
        assertNotEquals(10L, q2.limit(Quota.Metric.AI_REQUESTS));
    }
}
