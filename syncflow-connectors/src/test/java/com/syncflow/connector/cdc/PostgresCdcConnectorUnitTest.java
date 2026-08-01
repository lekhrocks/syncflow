package com.syncflow.connector.cdc;

import com.syncflow.core.cdc.CDCOperation;
import com.syncflow.core.model.ConnectionConfiguration;
import com.syncflow.core.model.ConnectorType;
import com.syncflow.core.spi.ConnectorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for PostgresCdcConnector covering event parsing, PK extraction,
 * and Debezium property generation — without starting the real Debezium engine.
 */
@DisplayName("PostgresCdcConnector")
class PostgresCdcConnectorUnitTest {

    private PostgresCdcConnector connector;

    @BeforeEach
    void setUp() {
        connector = new PostgresCdcConnector();
    }

    private ConnectionConfiguration config(String database) {
        return new ConnectionConfiguration(ConnectorType.POSTGRESQL, "localhost", 5432,
                database, "user", "pass", Map.of());
    }

    private ConnectorContext ctx(String database) {
        return new ConnectorContext(config(database), Map.of());
    }

    // ── connector type / capabilities ────────────────────────────────────────

    @Nested
    @DisplayName("connector identity")
    class Identity {

        @Test
        void typeIsPostgresql() {
            assertEquals(ConnectorType.POSTGRESQL, connector.type());
        }

        @Test
        void supportsCdc() {
            assertTrue(connector.capabilities().supportsCdc());
        }

        @Test
        void initialStatusIsInactive() {
            assertFalse(connector.isCdcActive());
        }
    }

    // ── specificProperties ───────────────────────────────────────────────────

    @Nested
    @DisplayName("specificProperties()")
    class SpecificProperties {

        @Test
        void slotNameScopedToDatabase() throws Exception {
            var props = invokeSpecificProperties(config("mydb"));
            assertTrue(props.getProperty("slot.name").contains("mydb"),
                    "slot.name should contain database name");
        }

        @Test
        void publicationNameScopedToDatabase() throws Exception {
            var props = invokeSpecificProperties(config("mydb"));
            assertTrue(props.getProperty("publication.name").contains("mydb"));
        }

        @Test
        void serverNameScopedToDatabase() throws Exception {
            var props = invokeSpecificProperties(config("mydb"));
            assertTrue(props.getProperty("database.server.name").contains("mydb"));
        }

        @Test
        void differentDatabasesDifferentSlotNames() throws Exception {
            var p1 = invokeSpecificProperties(config("db1"));
            var p2 = invokeSpecificProperties(config("db2"));
            assertNotEquals(p1.getProperty("slot.name"), p2.getProperty("slot.name"),
                    "Different databases must have different slot names to avoid conflicts");
        }

        @Test
        void pluginNameIspgoutput() throws Exception {
            var props = invokeSpecificProperties(config("db"));
            assertEquals("pgoutput", props.getProperty("plugin.name"));
        }

        @Test
        void specialCharsInDatabaseNameSanitized() throws Exception {
            var props = invokeSpecificProperties(config("my-db.prod"));
            var slot = props.getProperty("slot.name");
            assertFalse(slot.contains("-"), "Slot name must not contain hyphens");
            assertFalse(slot.contains("."), "Slot name must not contain dots");
        }

        private java.util.Properties invokeSpecificProperties(ConnectionConfiguration cfg)
                throws Exception {
            Method m = DebeziumCdcConnector.class.getDeclaredMethod(
                    "specificProperties", ConnectionConfiguration.class);
            m.setAccessible(true);
            return (Properties) m.invoke(connector, cfg);
        }
    }

    // ── event parsing via buildEvent ─────────────────────────────────────────

    @Nested
    @DisplayName("buildEvent() — INSERT")
    class InsertParsing {

        @Test
        void returnsInsertOperation() {
            var json = debeziumJson("c", null,
                    Map.of("id", 1, "name", "Alice"),
                    "public", "users", "0/1A2B3C4");
            var event = connector.buildEvent(changeEvent(null, json), ctx("mydb"));
            assertNotNull(event);
            assertEquals(CDCOperation.INSERT, event.operation());
        }

        @Test
        void afterPayloadPopulated() {
            var json = debeziumJson("c", null,
                    Map.of("id", 1, "name", "Alice"),
                    "public", "users", "0/1A");
            var event = connector.buildEvent(changeEvent(null, json), ctx("mydb"));
            assertNotNull(event.payload().after());
            assertEquals("Alice", event.payload().after().get("name"));
        }

        @Test
        void beforePayloadNullOnInsert() {
            var json = debeziumJson("c", null,
                    Map.of("id", 1, "name", "Alice"),
                    "public", "users", "0/1A");
            var event = connector.buildEvent(changeEvent(null, json), ctx("mydb"));
            assertNull(event.payload().before());
        }

        @Test
        void sourceSchemaAndTablePopulated() {
            var json = debeziumJson("c", null,
                    Map.of("id", 1), "myschema", "orders", "0/1A");
            var event = connector.buildEvent(changeEvent(null, json), ctx("mydb"));
            assertEquals("myschema", event.source().schema());
            assertEquals("orders", event.source().table());
        }

        @Test
        void lsnStoredInOffset() {
            var json = debeziumJson("c", null,
                    Map.of("id", 1), "public", "users", "0/ABCDEF");
            var event = connector.buildEvent(changeEvent(null, json), ctx("mydb"));
            assertEquals("0/ABCDEF", event.offset().offset().get("lsn"));
        }

        @Test
        void readOperationMappedToInsert() {
            var json = debeziumJson("r", null,
                    Map.of("id", 1), "public", "users", "0/1A");
            var event = connector.buildEvent(changeEvent(null, json), ctx("mydb"));
            assertNotNull(event);
            assertEquals(CDCOperation.INSERT, event.operation());
        }
    }

    @Nested
    @DisplayName("buildEvent() — UPDATE")
    class UpdateParsing {

        @Test
        void returnsUpdateOperation() {
            var json = debeziumJson("u",
                    Map.of("id", 1, "name", "OldName"),
                    Map.of("id", 1, "name", "NewName"),
                    "public", "users", "0/2A");
            var event = connector.buildEvent(changeEvent("{\"id\":1}", json), ctx("mydb"));
            assertNotNull(event);
            assertEquals(CDCOperation.UPDATE, event.operation());
        }

        @Test
        void beforeAndAfterBothPopulated() {
            var json = debeziumJson("u",
                    Map.of("id", 1, "name", "Old"),
                    Map.of("id", 1, "name", "New"),
                    "public", "users", "0/2A");
            var event = connector.buildEvent(changeEvent("{\"id\":1}", json), ctx("mydb"));
            assertNotNull(event.payload().before());
            assertNotNull(event.payload().after());
            assertEquals("Old", event.payload().before().get("name"));
            assertEquals("New", event.payload().after().get("name"));
        }
    }

    @Nested
    @DisplayName("buildEvent() — DELETE")
    class DeleteParsing {

        @Test
        void returnsDeleteOperation() {
            var json = debeziumJson("d",
                    Map.of("id", 1, "name", "Alice"),
                    null, "public", "users", "0/3A");
            var event = connector.buildEvent(changeEvent("{\"id\":1}", json), ctx("mydb"));
            assertNotNull(event);
            assertEquals(CDCOperation.DELETE, event.operation());
        }

        @Test
        void beforePopulatedAfterNullOnDelete() {
            var json = debeziumJson("d",
                    Map.of("id", 1, "name", "Alice"),
                    null, "public", "users", "0/3A");
            var event = connector.buildEvent(changeEvent("{\"id\":1}", json), ctx("mydb"));
            assertNotNull(event.payload().before());
            assertNull(event.payload().after());
        }
    }

    @Nested
    @DisplayName("buildEvent() — edge cases")
    class EdgeCases {

        @Test
        void nullValueReturnsNull() {
            var event = connector.buildEvent(changeEvent(null, null), ctx("mydb"));
            assertNull(event);
        }

        @Test
        void blankValueReturnsNull() {
            var event = connector.buildEvent(changeEvent(null, "  "), ctx("mydb"));
            assertNull(event);
        }

        @Test
        void unknownOpReturnsNull() {
            var json = debeziumJson("x", null,
                    Map.of("id", 1), "public", "users", "0/1A");
            var event = connector.buildEvent(changeEvent(null, json), ctx("mydb"));
            assertNull(event);
        }

        @Test
        void invalidJsonReturnsNull() {
            var event = connector.buildEvent(changeEvent(null, "NOT_JSON{{{"), ctx("mydb"));
            assertNull(event);
        }
    }

    @Nested
    @DisplayName("PK extraction")
    class PkExtraction {

        @Test
        void pkExtractedFromKeyEnvelope() {
            var json = debeziumJson("c", null,
                    Map.of("id", 42, "name", "Bob"),
                    "public", "users", "0/1A");
            var event = connector.buildEvent(changeEvent("{\"id\":42}", json), ctx("mydb"));
            assertNotNull(event);
            assertEquals(42, event.payload().primaryKeys().get("id"));
            assertEquals(1, event.payload().primaryKeys().size()); // only PK, not full row
        }

        @Test
        void pkFallsBackToIdColumnWhenNoKeyEnvelope() {
            var json = debeziumJson("c", null,
                    Map.of("id", 99, "name", "Fallback"),
                    "public", "users", "0/1A");
            var event = connector.buildEvent(changeEvent(null, json), ctx("mydb"));
            assertNotNull(event);
            assertTrue(event.payload().primaryKeys().containsKey("id"));
        }

        @Test
        void pkEmptyWhenNoKeyAndNoCommonColumn() {
            var json = debeziumJson("c", null,
                    Map.of("col1", "a", "col2", "b"),
                    "public", "custom_table", "0/1A");
            var event = connector.buildEvent(changeEvent(null, json), ctx("mydb"));
            assertNotNull(event);
            assertTrue(event.payload().primaryKeys().isEmpty());
        }

        @Test
        void pkUsesBeforeImageOnDelete() {
            var json = debeziumJson("d",
                    Map.of("id", 55, "name", "Gone"),
                    null, "public", "users", "0/3A");
            // No key envelope — should fall back to before image
            var event = connector.buildEvent(changeEvent(null, json), ctx("mydb"));
            assertNotNull(event);
            assertTrue(event.payload().primaryKeys().containsKey("id"));
        }
    }

    @Nested
    @DisplayName("offset tracking")
    class OffsetTracking {

        @Test
        void offsetEmptyBeforeAnyEvent() {
            assertTrue(connector.currentOffset().isEmpty());
        }

        @Test
        void offsetUpdatedAfterBuildEvent() {
            var json = debeziumJson("c", null,
                    Map.of("id", 1), "public", "users", "0/AABBCC");
            connector.buildEvent(changeEvent(null, json), ctx("mydb"));
            var offset = connector.currentOffset();
            assertEquals("0/AABBCC", offset.get("lsn"));
        }

        @Test
        void offsetContainsConnectorType() {
            var json = debeziumJson("c", null,
                    Map.of("id", 1), "public", "users", "0/1A");
            connector.buildEvent(changeEvent(null, json), ctx("mydb"));
            assertEquals("POSTGRESQL", connector.currentOffset().get("connectorType"));
        }
    }

    // ── Test helpers ──────────────────────────────────────────────────────────

    /**
     * Build a Debezium-style JSON payload string.
     */
    private String debeziumJson(String op,
            Map<String, Object> before,
            Map<String, Object> after,
            String schema,
            String table,
            String lsn) {
        var sb = new StringBuilder("{");
        sb.append("\"op\":\"").append(op).append("\",");
        if (before != null) {
            sb.append("\"before\":").append(toJson(before)).append(",");
        } else {
            sb.append("\"before\":null,");
        }
        if (after != null) {
            sb.append("\"after\":").append(toJson(after)).append(",");
        } else {
            sb.append("\"after\":null,");
        }
        sb.append("\"source\":{")
                .append("\"schema\":\"").append(schema).append("\",")
                .append("\"table\":\"").append(table).append("\",")
                .append("\"lsn\":\"").append(lsn).append("\",")
                .append("\"ts_ms\":1234567890")
                .append("}");
        sb.append("}");
        return sb.toString();
    }

    private String toJson(Map<String, Object> map) {
        var sb = new StringBuilder("{");
        map.forEach((k, v) -> {
            sb.append("\"").append(k).append("\":");
            if (v instanceof String)
                sb.append("\"").append(v).append("\"");
            else
                sb.append(v);
            sb.append(",");
        });
        if (!map.isEmpty())
            sb.deleteCharAt(sb.length() - 1);
        sb.append("}");
        return sb.toString();
    }

    /**
     * Simple ChangeEvent stub.
     */
    private io.debezium.engine.ChangeEvent<String, String> changeEvent(String key, String value) {
        return new io.debezium.engine.ChangeEvent<>() {

            @Override
            public String key() {
                return key;
            }

            @Override
            public String value() {
                return value;
            }

            @Override
            public String destination() {
                return "test";
            }

            @Override
            public Integer partition() {
                return null;
            }
        };
    }
}
