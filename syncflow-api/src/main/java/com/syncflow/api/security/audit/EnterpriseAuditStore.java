package com.syncflow.api.security.audit;

import com.syncflow.persistence.security.audit.entity.AuditRecordEntity;
import com.syncflow.persistence.security.audit.repository.AuditRecordRepository;
import com.syncflow.tenant.TenantId;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Enterprise audit records persisted to PostgreSQL. */
@Component
public class EnterpriseAuditStore {

    private final AuditRecordRepository repository;

    public EnterpriseAuditStore(AuditRecordRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public EnterpriseAuditRecord record(TenantId tenantId, String actor, String action,
            String resourceType, String resourceId,
            String details, String ipAddress) {
        var id = UUID.randomUUID();
        var record = new EnterpriseAuditRecord(id, tenantId, actor, action,
                resourceType, resourceId, details, ipAddress, false, Instant.now());
        var entity = toEntity(record);
        repository.save(entity);
        return record;
    }

    @Transactional(readOnly = true)
    public List<EnterpriseAuditRecord> list(TenantId tenantId, int limit) {
        return repository
                .findByTenantIdOrderByEventTimeDesc(tenantId.value(), PageRequest.of(0, Math.max(1, limit)))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    public boolean hardDelete() {
        return repository.count() == 0;
    }

    /** Compliance: GDPR right-to-delete — remove all records for a tenant. */
    @Transactional
    public void anonymize(UserDeletionRequest req) {
        repository.deleteAll(repository.findByTenantIdOrderByEventTimeDesc(
                req.tenantId().value(), PageRequest.of(0, 10_000)));
    }

    private AuditRecordEntity toEntity(EnterpriseAuditRecord r) {
        var e = new AuditRecordEntity();
        e.setId(r.id());
        e.setTenantId(r.tenantId().value());
        e.setActor(r.actor());
        e.setAction(r.action());
        e.setResourceType(r.resourceType());
        e.setResourceId(r.resourceId());
        e.setDetails(r.details());
        e.setIpAddress(r.ipAddress());
        e.setSuspicious(r.suspicious());
        e.setEventTime(r.timestamp());
        e.setCreatedAt(r.timestamp());
        return e;
    }

    private EnterpriseAuditRecord toDomain(AuditRecordEntity e) {
        return new EnterpriseAuditRecord(e.getId(), TenantId.from(e.getTenantId()),
                e.getActor(), e.getAction(), e.getResourceType(), e.getResourceId(),
                e.getDetails(), e.getIpAddress(), e.isSuspicious(), e.getEventTime());
    }

    public record UserDeletionRequest(TenantId tenantId) {
    }
}
