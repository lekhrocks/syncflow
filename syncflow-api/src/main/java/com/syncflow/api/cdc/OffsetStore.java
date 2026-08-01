package com.syncflow.api.cdc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncflow.api.cdc.repository.CdcOffsetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Persists the last known WAL/LSN/binlog position per pipeline to PostgreSQL
 * so CDC can resume from the correct position after a restart.
 */
@Component
public class OffsetStore {

    private static final Logger log = LoggerFactory.getLogger(OffsetStore.class);
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {
    };

    private final CdcOffsetRepository repository;
    private final ObjectMapper objectMapper;

    public OffsetStore(CdcOffsetRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void save(String pipelineId, Map<String, String> offset) {
        if (offset == null || offset.isEmpty()) {
            return;
        }
        try {
            var connectorType = offset.getOrDefault("connectorType", "UNKNOWN");
            var offsetJson = objectMapper.writeValueAsString(offset);
            repository.upsert(pipelineId, connectorType, offsetJson);
            log.debug("Saved CDC offset for pipeline={} offset={}", pipelineId, offsetJson);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize CDC offset for pipeline={}", pipelineId, e);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, String> get(String pipelineId) {
        return repository.findById(pipelineId)
                .map(entity -> {
                    try {
                        return objectMapper.<Map<String, String>>readValue(
                                entity.getOffsetData(), MAP_TYPE);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialize CDC offset for pipeline={}", pipelineId, e);
                        return Map.<String, String>of();
                    }
                })
                .orElse(Map.of());
    }

    @Transactional
    public void delete(String pipelineId) {
        repository.deleteById(pipelineId);
    }
}
