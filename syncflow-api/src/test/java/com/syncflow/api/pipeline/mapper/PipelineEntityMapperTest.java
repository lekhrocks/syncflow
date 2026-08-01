package com.syncflow.api.pipeline.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.syncflow.api.pipeline.entity.PipelineEntity;
import com.syncflow.core.model.ConnectionConfiguration;
import com.syncflow.core.model.ConnectorType;
import com.syncflow.core.model.Pipeline;
import com.syncflow.core.model.PipelineStatus;
import com.syncflow.core.model.TransformationConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("PipelineEntityMapper")
class PipelineEntityMapperTest {

    private PipelineEntityMapper mapper;
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        var objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        jsonMapper = new JsonMapper(objectMapper);
        mapper = new PipelineEntityMapperImpl();
    }

    private Pipeline buildPipeline() {
        var source = new ConnectionConfiguration(ConnectorType.POSTGRESQL, "localhost", 5432,
                "syncflow", "admin", "secret", Map.of());
        var dest = new ConnectionConfiguration(ConnectorType.MYSQL, "remote-host", 3306,
                "target", "user", "pass", Map.of());
        var mapping = new TransformationConfiguration(
                List.of("users"), List.of(), Map.of("id", "user_id"), Map.of());
        var p = new Pipeline("test-pipeline", source, dest, mapping);
        return p;
    }

    @Nested
    @DisplayName("toEntity")
    class ToEntity {

        @Test
        void mapsIdCorrectly() {
            var pipeline = buildPipeline();
            var entity = mapper.toEntity(pipeline, jsonMapper);
            assertEquals(pipeline.getId(), entity.getId());
        }

        @Test
        void mapsNameCorrectly() {
            var pipeline = buildPipeline();
            var entity = mapper.toEntity(pipeline, jsonMapper);
            assertEquals("test-pipeline", entity.getName());
        }

        @Test
        void mapsStatusAsString() {
            var pipeline = buildPipeline();
            var entity = mapper.toEntity(pipeline, jsonMapper);
            assertEquals("CREATED", entity.getStatus());
        }

        @Test
        void serializesSourceToJson() {
            var pipeline = buildPipeline();
            var entity = mapper.toEntity(pipeline, jsonMapper);
            assertNotNull(entity.getSource());
            assert entity.getSource().contains("POSTGRESQL");
            assert entity.getSource().contains("localhost");
        }

        @Test
        void serializesDestinationToJson() {
            var pipeline = buildPipeline();
            var entity = mapper.toEntity(pipeline, jsonMapper);
            assertNotNull(entity.getDestination());
            assert entity.getDestination().contains("MYSQL");
            assert entity.getDestination().contains("remote-host");
        }

        @Test
        void serializesMappingToJson() {
            var pipeline = buildPipeline();
            var entity = mapper.toEntity(pipeline, jsonMapper);
            assertNotNull(entity.getMapping());
            assert entity.getMapping().contains("users");
        }

        @Test
        void mapsNullMappingToNull() {
            var source = new ConnectionConfiguration(ConnectorType.POSTGRESQL, "h", 5432, "db",
                    "u", "p", Map.of());
            var pipeline = new Pipeline("no-mapping", source, source, null);
            var entity = mapper.toEntity(pipeline, jsonMapper);
            assertNull(entity.getMapping());
        }

        @Test
        void preservesTimestamps() {
            var pipeline = buildPipeline();
            var entity = mapper.toEntity(pipeline, jsonMapper);
            assertNotNull(entity.getCreatedAt());
            assertNotNull(entity.getUpdatedAt());
        }
    }

    @Nested
    @DisplayName("toDomain")
    class ToDomain {

        @Test
        void roundTripPreservesId() {
            var pipeline = buildPipeline();
            var entity = mapper.toEntity(pipeline, jsonMapper);
            var result = mapper.toDomain(entity, jsonMapper);
            assertEquals(pipeline.getId(), result.getId());
        }

        @Test
        void roundTripPreservesName() {
            var pipeline = buildPipeline();
            var entity = mapper.toEntity(pipeline, jsonMapper);
            var result = mapper.toDomain(entity, jsonMapper);
            assertEquals("test-pipeline", result.getName());
        }

        @Test
        void roundTripPreservesStatus() {
            var pipeline = buildPipeline();
            var entity = mapper.toEntity(pipeline, jsonMapper);
            var result = mapper.toDomain(entity, jsonMapper);
            assertEquals(PipelineStatus.CREATED, result.getStatus());
        }

        @Test
        void roundTripPreservesSourceConnectorType() {
            var pipeline = buildPipeline();
            var entity = mapper.toEntity(pipeline, jsonMapper);
            var result = mapper.toDomain(entity, jsonMapper);
            assertEquals(ConnectorType.POSTGRESQL, result.getSource().connectorType());
        }

        @Test
        void roundTripPreservesDestinationHost() {
            var pipeline = buildPipeline();
            var entity = mapper.toEntity(pipeline, jsonMapper);
            var result = mapper.toDomain(entity, jsonMapper);
            assertEquals("remote-host", result.getDestination().host());
        }

        @Test
        void roundTripPreservesMappingIncludedTables() {
            var pipeline = buildPipeline();
            var entity = mapper.toEntity(pipeline, jsonMapper);
            var result = mapper.toDomain(entity, jsonMapper);
            assertNotNull(result.getMapping());
            assertEquals(List.of("users"), result.getMapping().includedTables());
        }

        @Test
        void roundTripWithNullMappingReturnsNull() {
            var source = new ConnectionConfiguration(ConnectorType.POSTGRESQL, "h", 5432, "db",
                    "u", "p", Map.of());
            var pipeline = new Pipeline("no-mapping", source, source, null);
            var entity = mapper.toEntity(pipeline, jsonMapper);
            var result = mapper.toDomain(entity, jsonMapper);
            assertNull(result.getMapping());
        }

        @Test
        void differentStatusesRoundTrip() {
            for (var status : PipelineStatus.values()) {
                var entity = new PipelineEntity();
                entity.setId("test-id");
                entity.setName("test");
                entity.setStatus(status.name());
                var source = new ConnectionConfiguration(ConnectorType.POSTGRESQL, "h", 5432,
                        "db", "u", "p", Map.of());
                entity.setSource(jsonMapper.fromConnectionConfiguration(source));
                entity.setDestination(jsonMapper.fromConnectionConfiguration(source));
                entity.setCreatedAt(Instant.now());
                entity.setUpdatedAt(Instant.now());
                var result = mapper.toDomain(entity, jsonMapper);
                assertEquals(status, result.getStatus());
            }
        }
    }
}
