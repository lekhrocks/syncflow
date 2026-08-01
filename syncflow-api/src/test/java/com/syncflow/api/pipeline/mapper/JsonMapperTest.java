package com.syncflow.api.pipeline.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.syncflow.core.model.ConnectionConfiguration;
import com.syncflow.core.model.ConnectorType;
import com.syncflow.core.model.TransformationConfiguration;
import com.syncflow.core.pipeline.DestinationReference;
import com.syncflow.core.pipeline.PipelineSettings;
import com.syncflow.core.pipeline.SourceReference;
import com.syncflow.core.pipeline.SyncMode;
import com.syncflow.core.pipeline.mapping.TableMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("JsonMapper")
class JsonMapperTest {

    private JsonMapper mapper;

    @BeforeEach
    void setUp() {
        var objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper = new JsonMapper(objectMapper);
    }

    @Nested
    @DisplayName("SourceReference round-trip")
    class SourceReferenceRoundTrip {

        @Test
        void serializesAndDeserializesCorrectly() {
            var source = new SourceReference("conn-1", "public", "users");
            var json = mapper.fromSourceReference(source);
            var result = mapper.toSourceReference(json);
            assertEquals(source.connectionId(), result.connectionId());
            assertEquals(source.schema(), result.schema());
            assertEquals(source.tableOrCollection(), result.tableOrCollection());
        }

        @Test
        void jsonContainsExpectedFields() {
            var source = new SourceReference("conn-abc", "myschema", "orders");
            var json = mapper.fromSourceReference(source);
            assertNotNull(json);
            assert json.contains("conn-abc");
            assert json.contains("myschema");
            assert json.contains("orders");
        }
    }

    @Nested
    @DisplayName("DestinationReference round-trip")
    class DestinationReferenceRoundTrip {

        @Test
        void serializesAndDeserializesCorrectly() {
            var dest = new DestinationReference("conn-2", "public", "users_copy", "UPSERT");
            var json = mapper.fromDestinationReference(dest);
            var result = mapper.toDestinationReference(json);
            assertEquals(dest.connectionId(), result.connectionId());
            assertEquals(dest.schema(), result.schema());
            assertEquals(dest.tableOrCollection(), result.tableOrCollection());
            assertEquals(dest.writeMode(), result.writeMode());
        }

        @Test
        void defaultWriteModePreserved() {
            var dest = new DestinationReference("conn-2", "s", "t", null);
            var json = mapper.fromDestinationReference(dest);
            var result = mapper.toDestinationReference(json);
            assertEquals("UPSERT", result.writeMode());
        }
    }

    @Nested
    @DisplayName("PipelineSettings round-trip")
    class PipelineSettingsRoundTrip {

        @Test
        void serializesAndDeserializesCorrectly() {
            var settings = new PipelineSettings(SyncMode.CDC_INCREMENTAL, 500, 5, true, false,
                    Map.of("key", "value"));
            var json = mapper.fromPipelineSettings(settings);
            var result = mapper.toPipelineSettings(json);
            assertEquals(settings.syncMode(), result.syncMode());
            assertEquals(settings.batchSize(), result.batchSize());
            assertEquals(settings.maxRetries(), result.maxRetries());
            assertEquals(settings.skipConstraints(), result.skipConstraints());
            assertEquals(settings.skipIndexes(), result.skipIndexes());
            assertEquals("value", result.properties().get("key"));
        }

        @Test
        void defaultSettingsRoundTrip() {
            var settings = PipelineSettings.defaults();
            var json = mapper.fromPipelineSettings(settings);
            var result = mapper.toPipelineSettings(json);
            assertEquals(SyncMode.FULL_SNAPSHOT, result.syncMode());
            assertEquals(1000, result.batchSize());
        }
    }

    @Nested
    @DisplayName("TableMappings round-trip")
    class TableMappingsRoundTrip {

        @Test
        void emptyListRoundTrips() {
            var json = mapper.fromTableMappings(List.of());
            var result = mapper.toTableMappings(json);
            assertNotNull(result);
            assert result.isEmpty();
        }

        @Test
        void nonEmptyListRoundTrips() {
            var tm = new TableMapping("users", "users_copy", null, null, List.of(), List.of(), List.of(), null);
            var json = mapper.fromTableMappings(List.of(tm));
            var result = mapper.toTableMappings(json);
            assertEquals(1, result.size());
            assertEquals("users", result.get(0).sourceTable());
            assertEquals("users_copy", result.get(0).destinationTable());
        }
    }

    @Nested
    @DisplayName("ConnectionConfiguration round-trip")
    class ConnectionConfigurationRoundTrip {

        @Test
        void serializesAndDeserializesCorrectly() {
            var config = new ConnectionConfiguration(ConnectorType.POSTGRESQL, "localhost", 5432,
                    "syncflow", "admin", "secret", Map.of("ssl", "true"));
            var json = mapper.fromConnectionConfiguration(config);
            var result = mapper.toConnectionConfiguration(json);
            assertEquals(config.connectorType(), result.connectorType());
            assertEquals(config.host(), result.host());
            assertEquals(config.port(), result.port());
            assertEquals(config.database(), result.database());
            assertEquals("true", result.properties().get("ssl"));
        }
    }

    @Nested
    @DisplayName("TransformationConfiguration round-trip")
    class TransformationConfigurationRoundTrip {

        @Test
        void serializesAndDeserializesCorrectly() {
            var config = new TransformationConfiguration(
                    List.of("users", "orders"),
                    List.of("audit_log"),
                    Map.of("src_col", "dest_col"),
                    Map.of("full_name", "CONCAT(first, last)"));
            var json = mapper.fromTransformationConfiguration(config);
            var result = mapper.toTransformationConfiguration(json);
            assertEquals(config.includedTables(), result.includedTables());
            assertEquals(config.excludedTables(), result.excludedTables());
            assertEquals("dest_col", result.columnMappings().get("src_col"));
        }

        @Test
        void nullTransformationConfigurationReturnsNull() {
            var json = mapper.fromTransformationConfiguration(null);
            assertNull(json);
            assertNull(mapper.toTransformationConfiguration(null));
        }
    }

    @Nested
    @DisplayName("toJson / fromJson generic")
    class GenericJsonMethods {

        @Test
        void toJsonSerializesArbitraryObject() {
            var json = mapper.toJson(Map.of("a", 1, "b", "two"));
            assertNotNull(json);
            assert json.contains("\"a\"");
        }

        @Test
        void fromJsonDeserializesToExpectedType() {
            var json = "{\"connectionId\":\"conn-1\",\"schema\":\"s\",\"tableOrCollection\":\"t\"}";
            var result = mapper.fromJson(json, SourceReference.class);
            assertEquals("conn-1", result.connectionId());
        }

        @Test
        void fromJsonInvalidJsonThrowsIllegalState() {
            assertThrows(IllegalStateException.class,
                    () -> mapper.fromJson("NOT_JSON{{{", SourceReference.class));
        }
    }
}
