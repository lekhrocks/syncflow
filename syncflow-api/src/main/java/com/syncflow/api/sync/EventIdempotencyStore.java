package com.syncflow.api.sync;

import com.syncflow.persistence.sync.entity.ProcessedEventEntity;
import com.syncflow.persistence.sync.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * JPA-backed idempotency store.
 * Replaces the in-memory {@code ConcurrentHashMap<String>} set so processed
 * event IDs survive restarts and prevent duplicate processing after a crash.
 *
 * Entries are automatically purged after 7 days by a scheduled task.
 */
@Component
@Transactional
public class EventIdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(EventIdempotencyStore.class);
    private static final int RETENTION_DAYS = 7;

    private final ProcessedEventRepository repository;

    public EventIdempotencyStore(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public boolean isProcessed(String eventId) {
        return repository.existsByEventId(eventId);
    }

    /**
     * Atomically mark the event processed and return whether THIS call was the
     * one that did it (true) or the event was already processed (false).
     * The single SQL {@code INSERT ... ON CONFLICT DO NOTHING} ensures exactly-once
     * semantics even when multiple workers race for the same event.
     */
    public boolean markProcessedIfAbsent(String eventId, String tenantId, String pipelineId) {
        // Tenant is implicit in the pipelineId scoping — the event_id is
        // globally unique per CDC source, so a composite key isn't needed.
        int rows = repository.insertIfAbsent(eventId, pipelineId, Instant.now());
        return rows > 0;
    }

    public void markProcessed(String eventId) {
        markProcessed(eventId, "unknown");
    }

    public void markProcessed(String eventId, String pipelineId) {
        repository.save(new ProcessedEventEntity(eventId, pipelineId, Instant.now()));
    }

    public void evict(String eventId) {
        repository.deleteById(eventId);
    }

    @Transactional(readOnly = true)
    public long size() {
        return repository.count();
    }

    /** Nightly cleanup: remove entries older than retention period. */
    @Scheduled(cron = "0 0 3 * * *") // 3am daily
    public void purgeExpired() {
        var cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        int deleted = repository.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("Purged {} expired idempotency entries older than {}d", deleted, RETENTION_DAYS);
        }
    }
}
