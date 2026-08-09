package com.syncflow.api.security.quota;

import com.fasterxml.jackson.core.type.TypeReference;
import com.syncflow.api.runtimestate.RuntimeStateJson;
import com.syncflow.api.security.quota.entity.QuotaEntity;
import com.syncflow.api.security.quota.repository.QuotaRepository;
import com.syncflow.tenant.TenantId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class QuotaEngine {

    private static final TypeReference<java.util.Map<Quota.Metric, Long>> LIMITS_TYPE = new TypeReference<>() {
    };

    private final QuotaRepository repository;
    private final RuntimeStateJson json;
    // Fast-path read cache; durable source of truth is the quotas table.
    private final java.util.Map<TenantId, Quota> cache = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public QuotaEngine(QuotaRepository repository, RuntimeStateJson json) {
        this.repository = repository;
        this.json = json;
    }

    /** Unit-test seam: in-memory engine without a repository. */
    public QuotaEngine() {
        this.repository = null;
        this.json = null;
    }

    @Transactional(readOnly = true)
    public Quota getQuota(TenantId tenantId) {
        return cache.computeIfAbsent(tenantId, t -> load(t).orElse(Quota.defaults()));
    }

    @Transactional
    public void setQuota(TenantId tenantId, Quota quota) {
        cache.put(tenantId, quota);
        if (repository == null)
            return; // unit-test seam
        var entity = repository.findById(tenantId.value()).orElseGet(QuotaEntity::new);
        entity.setTenantId(tenantId.value());
        entity.setLimits(json.toJson(quota.limits()));
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public QuotaResult checkLimit(TenantId tenantId, Quota.Metric metric, long current) {
        var quota = getQuota(tenantId);
        var limit = quota.limit(metric);
        if (limit == -1)
            return new QuotaResult(false, -1, current, metric);
        return new QuotaResult(current >= limit, limit, current, metric);
    }

    private java.util.Optional<Quota> load(TenantId tenantId) {
        if (repository == null)
            return java.util.Optional.empty(); // unit-test seam
        return repository.findById(tenantId.value())
                .map(e -> new Quota(json.fromJson(e.getLimits(), LIMITS_TYPE)));
    }

    public record QuotaResult(boolean exceeded, long limit, long current, Quota.Metric metric) {
    }
}
