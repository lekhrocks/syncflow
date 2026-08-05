package com.syncflow.api.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncflow.api.sync.entity.DeadLetterEventEntity;
import com.syncflow.api.sync.repository.DeadLetterEventRepository;
import com.syncflow.core.cdc.CDCEvent;
import com.syncflow.core.sync.FailureReason;
import com.syncflow.core.sync.dlq.DeadLetterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JPA-backed Dead Letter Queue.
 * Replaces the in-memory {@code ConcurrentHashMap} store so failed events
 * survive application restarts and can be inspected / replayed via the DB.
 */
@Component
@Transactional
public class DeadLetterQueue {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterQueue.class);

    private final DeadLetterEventRepository repository;
    private final ObjectMapper objectMapper;

    public DeadLetterQueue(DeadLetterEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void add(String pipelineId, CDCEvent event, FailureReason reason, int retryCount) {
        try {
            var entity = new DeadLetterEventEntity();
            entity.setId(UUID.randomUUID().toString());
            entity.setPipelineId(pipelineId);
            // event may be null when enqueuing a failure without an associated CDC record
            entity.setEventId(event != null ? event.header().eventId() : null);
            entity.setEventData(event != null ? objectMapper.writeValueAsString(event) : null);
            entity.setErrorMessage(reason.message());
            entity.setFailureType(reason.retryable() ? "TRANSIENT" : "PERMANENT");
            entity.setRetryCount(retryCount);
            entity.setCreatedAt(Instant.now());
            repository.save(entity);
            log.warn("Event added to DLQ pipeline={} eventId={} reason={} retries={}",
                    pipelineId, entity.getEventId(), reason.code(), retryCount);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize CDC event for DLQ pipeline={}", pipelineId, e);
        }
    }

    @Transactional(readOnly = true)
    public DeadLetterEvent get(String id) {
        return repository.findById(id).map(this::toDomain).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<DeadLetterEvent> list(String pipelineId) {
        if (pipelineId == null) {
            return repository.findAll().stream().map(this::toDomain).toList();
        }
        return repository.findByPipelineIdOrderByCreatedAtDesc(pipelineId)
                .stream().map(this::toDomain).toList();
    }

    public void delete(String id) {
        repository.deleteById(id);
    }

    public void clearAll() {
        repository.deleteAll();
    }

    public void replay(String id) {
        repository.markReplayed(id);
        log.info("DLQ event marked for replay id={}", id);
    }

    @Transactional(readOnly = true)
    public long count() {
        return repository.count();
    }

    // ── mapping ───────────────────────────────────────────────────────────────

    private DeadLetterEvent toDomain(DeadLetterEventEntity e) {
        CDCEvent event = null;
        if (e.getEventData() != null) {
            try {
                event = objectMapper.readValue(e.getEventData(), CDCEvent.class);
            } catch (JsonProcessingException ex) {
                log.error("Failed to deserialize DLQ event data id={}", e.getId(), ex);
            }
        }
        var reason = "PERMANENT".equals(e.getFailureType())
                ? FailureReason.permanentError(e.getErrorMessage())
                : FailureReason.transientError(e.getErrorMessage());
        return new DeadLetterEvent(e.getId(), e.getPipelineId(), event, reason,
                e.getRetryCount(), e.getCreatedAt(), e.getReplayCount());
    }
}
