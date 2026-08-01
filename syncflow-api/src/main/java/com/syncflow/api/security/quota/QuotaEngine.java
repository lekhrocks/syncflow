package com.syncflow.api.security.quota;

import com.syncflow.tenant.TenantId;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class QuotaEngine {

    private final java.util.Map<TenantId, Quota> quotas = new ConcurrentHashMap<>();

    public Quota getQuota(TenantId tenantId) {
        return quotas.computeIfAbsent(tenantId, k -> Quota.defaults());
    }

    public void setQuota(TenantId tenantId, Quota quota) {
        quotas.put(tenantId, quota);
    }

    public QuotaResult checkLimit(TenantId tenantId, Quota.Metric metric, long current) {
        var quota = getQuota(tenantId);
        var limit = quota.limit(metric);
        if (limit == -1)
            return new QuotaResult(false, -1, current, metric);
        return new QuotaResult(current >= limit, limit, current, metric);
    }

    public record QuotaResult(boolean exceeded, long limit, long current, Quota.Metric metric) {
    }
}
