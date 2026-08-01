package com.syncflow.api.cdc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncflow.api.cdc.entity.CdcOffsetEntity;
import com.syncflow.api.cdc.repository.CdcOffsetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OffsetStore")
class OffsetStoreTest {

    @Mock
    private CdcOffsetRepository repository;

    private OffsetStore offsetStore;

    @BeforeEach
    void setUp() {
        offsetStore = new OffsetStore(repository, new ObjectMapper());
    }

    @Nested
    @DisplayName("save()")
    class Save {

        @Test
        void persistsOffsetToRepository() {
            offsetStore.save("pipeline-1",
                    Map.of("lsn", "0/1A2B3C", "connectorType", "POSTGRESQL"));

            verify(repository).upsert(eq("pipeline-1"), eq("POSTGRESQL"), any(String.class));
        }

        @Test
        void doesNotSaveEmptyOffset() {
            offsetStore.save("pipeline-1", Map.of());
            verify(repository, never()).upsert(any(), any(), any());
        }

        @Test
        void doesNotSaveNullOffset() {
            offsetStore.save("pipeline-1", null);
            verify(repository, never()).upsert(any(), any(), any());
        }

        @Test
        void offsetJsonContainsLsn() {
            var captor = ArgumentCaptor.forClass(String.class);
            offsetStore.save("p-1",
                    Map.of("lsn", "0/AABBCC", "connectorType", "POSTGRESQL"));

            verify(repository).upsert(eq("p-1"), eq("POSTGRESQL"), captor.capture());
            assertTrue(captor.getValue().contains("AABBCC"));
        }

        @Test
        void usesUnknownConnectorTypeWhenMissing() {
            offsetStore.save("p-1", Map.of("lsn", "0/1A"));
            verify(repository).upsert(eq("p-1"), eq("UNKNOWN"), any());
        }
    }

    @Nested
    @DisplayName("get()")
    class Get {

        @Test
        void returnsEmptyMapWhenNotFound() {
            when(repository.findById("missing")).thenReturn(Optional.empty());
            var result = offsetStore.get("missing");
            assertTrue(result.isEmpty());
        }

        @Test
        void deserializesStoredOffset() {
            var entity = new CdcOffsetEntity();
            entity.setPipelineId("p-1");
            entity.setConnectorType("POSTGRESQL");
            entity.setOffsetData("{\"lsn\":\"0/AABBCC\",\"connectorType\":\"POSTGRESQL\"}");
            entity.setSavedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());

            when(repository.findById("p-1")).thenReturn(Optional.of(entity));

            var result = offsetStore.get("p-1");
            assertEquals("0/AABBCC", result.get("lsn"));
            assertEquals("POSTGRESQL", result.get("connectorType"));
        }

        @Test
        void returnsEmptyMapOnMalformedJson() {
            var entity = new CdcOffsetEntity();
            entity.setPipelineId("p-bad");
            entity.setOffsetData("NOT_VALID_JSON{{{");
            entity.setSavedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());

            when(repository.findById("p-bad")).thenReturn(Optional.of(entity));

            var result = offsetStore.get("p-bad");
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        void delegatesToRepository() {
            offsetStore.delete("p-1");
            verify(repository).deleteById("p-1");
        }
    }

    @Nested
    @DisplayName("round-trip save then get")
    class RoundTrip {

        @Test
        void savedOffsetCanBeRetrieved() {
            var offset = Map.of("lsn", "0/ABC123", "connectorType", "POSTGRESQL");
            var captor = ArgumentCaptor.forClass(String.class);

            offsetStore.save("p-rt", offset);
            verify(repository).upsert(eq("p-rt"), eq("POSTGRESQL"), captor.capture());

            // Simulate what the DB would return
            var entity = new CdcOffsetEntity();
            entity.setPipelineId("p-rt");
            entity.setConnectorType("POSTGRESQL");
            entity.setOffsetData(captor.getValue());
            entity.setSavedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());
            when(repository.findById("p-rt")).thenReturn(Optional.of(entity));

            var retrieved = offsetStore.get("p-rt");
            assertEquals("0/ABC123", retrieved.get("lsn"));
        }
    }
}
