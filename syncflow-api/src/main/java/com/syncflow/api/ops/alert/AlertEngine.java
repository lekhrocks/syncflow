package com.syncflow.api.ops.alert;

import com.syncflow.persistence.ops.alert.entity.AlertEventEntity;
import com.syncflow.persistence.ops.alert.repository.AlertEventRepository;
import com.syncflow.tenant.TenantSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AlertEngine {

    private final AlertEventRepository repository;
    private final AtomicLong counter = new AtomicLong(0);

    @Autowired
    public AlertEngine(AlertEventRepository repository) {
        this.repository = repository;
    }

    /** Unit-test seam: in-memory engine without a repository. */
    public AlertEngine() {
        this.repository = null;
    }

    @Transactional
    public AlertEvent raise(String name, String message, AlertSeverity severity, String source) {
        return raise(name, message, severity, source, null, null);
    }

    @Transactional
    public AlertEvent raise(String name, String message, AlertSeverity severity,
            String source, String pipelineId, String connectionId) {
        var id = "alert-" + counter.incrementAndGet();
        var event = new AlertEvent(id, name, message, severity, source,
                pipelineId, connectionId, Instant.now(), false);
        if (repository != null)
            repository.save(toEntity(event));
        return event;
    }

    @Transactional
    public void acknowledge(String id) {
        if (repository == null)
            return;
        repository.findById(id).ifPresent(e -> {
            e.setAcknowledged(true);
            repository.save(e);
        });
    }

    @Transactional(readOnly = true)
    public List<AlertEvent> active() {
        if (repository == null)
            return List.of();
        return repository
                .findByTenantIdAndAcknowledgedOrderByEventTimeDesc(TenantSupport.tenantId(), false)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AlertEvent> all() {
        if (repository == null)
            return List.of();
        return repository.findTop500ByTenantIdOrderByEventTimeDesc(TenantSupport.tenantId())
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Transactional(readOnly = true)
    public long count() {
        if (repository == null)
            return 0;
        return repository.count();
    }

    @Transactional
    public void clearAcknowledged() {
        if (repository == null)
            return;
        repository.deleteByTenantIdAndAcknowledged(TenantSupport.tenantId(), true);
    }

    private AlertEventEntity toEntity(AlertEvent v) {
        var e = new AlertEventEntity();
        e.setId(v.id());
        e.setTenantId(TenantSupport.tenantId());
        e.setName(v.name());
        e.setMessage(v.message());
        e.setSeverity(v.severity().name());
        e.setSource(v.source());
        e.setPipelineId(v.pipelineId());
        e.setConnectionId(v.connectionId());
        e.setEventTime(v.timestamp());
        e.setAcknowledged(v.acknowledged());
        e.setCreatedAt(v.timestamp());
        return e;
    }

    private AlertEvent toDomain(AlertEventEntity e) {
        return new AlertEvent(e.getId(), e.getName(), e.getMessage(),
                AlertSeverity.valueOf(e.getSeverity()), e.getSource(),
                e.getPipelineId(), e.getConnectionId(), e.getEventTime(), e.isAcknowledged());
    }
}
