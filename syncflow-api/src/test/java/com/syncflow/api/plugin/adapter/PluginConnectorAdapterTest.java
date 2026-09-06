package com.syncflow.api.plugin.adapter;

import com.syncflow.core.cdc.CDCEvent;
import com.syncflow.core.cdc.CDCOperation;
import com.syncflow.core.cdc.CaptureStatus;
import com.syncflow.core.model.ConnectionConfiguration;
import com.syncflow.core.model.ConnectorType;
import com.syncflow.core.snapshot.BatchInformation;
import com.syncflow.core.spi.ConnectorCapabilities;
import com.syncflow.core.spi.ConnectorContext;
import com.syncflow.core.spi.ConnectorHealth;
import com.syncflow.plugin.descriptor.PluginDescriptor;
import com.syncflow.plugin.spi.CdcProvider;
import com.syncflow.plugin.spi.PluginConnector;
import com.syncflow.plugin.spi.PluginContext;
import com.syncflow.plugin.spi.SnapshotProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginConnectorAdapterTest {

    private static PluginDescriptor desc(String pluginId, String connectorType) {
        return new PluginDescriptor(pluginId, "Test", "test", "1.0",
                "test plugin", connectorType, List.of(), "1", "1",
                List.of(), "MIT", null, null);
    }

    private static ConnectionConfiguration cfg() {
        return new ConnectionConfiguration(ConnectorType.GENERIC_PLUGIN,
                "host", 5432, "db", "user", "pass", Map.of("k", "v"));
    }

    private static ConnectorContext ctx() {
        return new ConnectorContext(cfg(), Map.of());
    }

    private static com.syncflow.plugin.capabilities.ConnectorCapabilities capsAll() {
        return new com.syncflow.plugin.capabilities.ConnectorCapabilities(
                true, true, true, true, true, true);
    }

    private static com.syncflow.plugin.capabilities.ConnectorCapabilities capsNone() {
        return new com.syncflow.plugin.capabilities.ConnectorCapabilities(
                false, false, false, false, false, false);
    }

    private static PluginConnector pluginWith(String type, String health,
            com.syncflow.plugin.capabilities.ConnectorCapabilities caps,
            List<Map<String, Object>> cols) {
        return new PluginConnector() {

            @Override
            public PluginDescriptor descriptor() {
                return desc("p1", type);
            }
            @Override
            public com.syncflow.plugin.capabilities.ConnectorCapabilities capabilities() {
                return caps;
            }
            @Override
            public String health() {
                return health;
            }
            @Override
            public Map<String, String> metadata() {
                return Map.of("v", "1");
            }
            @Override
            public List<String> discoverSchemas(PluginContext c) {
                return List.of("public");
            }
            @Override
            public List<String> discoverTables(PluginContext c, String s) {
                return List.of("t");
            }
            @Override
            public List<Map<String, Object>> discoverColumns(PluginContext c, String s, String t) {
                return cols;
            }
        };
    }

    @Test
    void type_resolves_to_known_enum() {
        var adapter = new PluginConnectorAdapter(
                pluginWith("postgresql", "UP", capsAll(),
                        List.of(Map.of("name", "id", "type", "int", "primaryKey", true))),
                desc("p1", "postgresql"));
        assertEquals(ConnectorType.POSTGRESQL, adapter.type());
    }

    @Test
    void type_resolves_to_generic_plugin_for_unknown() {
        var adapter = new PluginConnectorAdapter(
                pluginWith("rocket-db", "UP", capsAll(), List.of()),
                desc("p1", "rocket-db"));
        assertEquals(ConnectorType.GENERIC_PLUGIN, adapter.type());
    }

    @Test
    void capabilities_translates_all_six_to_five() {
        var adapter = new PluginConnectorAdapter(
                pluginWith("x", "UP", capsAll(), List.of()),
                desc("p", "x"));
        ConnectorCapabilities core = adapter.capabilities();
        assertTrue(core.supportsCdc());
        assertTrue(core.supportsSnapshot());
        assertTrue(core.supportsSchemaDiscovery());
        assertTrue(core.supportsTransactions());
        assertTrue(core.supportsOffsetTracking());
    }

    @Test
    void capabilities_zero_maps_to_none() {
        var adapter = new PluginConnectorAdapter(
                pluginWith("x", "UP", capsNone(), List.of()),
                desc("p", "x"));
        assertFalse(adapter.capabilities().supportsSnapshot());
    }

    @Test
    void health_parses_all_states() {
        for (var input : new String[]{"UP", "DOWN", "DEGRADED", "UNKNOWN", "garbage", null, "  up  "}) {
            var adapter = new PluginConnectorAdapter(
                    pluginWith("x", input, capsNone(), List.of()),
                    desc("p", "x"));
            var status = adapter.health().status();
            if (input == null) {
                assertEquals(ConnectorHealth.Status.UNKNOWN, status);
            } else {
                var up = input.trim().toUpperCase();
                var expected = switch (up) {
                    case "UP" -> ConnectorHealth.Status.UP;
                    case "DOWN" -> ConnectorHealth.Status.DOWN;
                    case "DEGRADED" -> ConnectorHealth.Status.DEGRADED;
                    default -> ConnectorHealth.Status.UNKNOWN;
                };
                assertEquals(expected, status, "input=" + input);
            }
        }
    }

    @Test
    void connect_marks_connected() {
        var adapter = new PluginConnectorAdapter(
                pluginWith("x", "UP", capsNone(), List.of()),
                desc("p", "x"));
        assertFalse(adapter.isConnected());
        adapter.connect(ctx());
        assertTrue(adapter.isConnected());
        adapter.disconnect();
        assertFalse(adapter.isConnected());
    }

    @Test
    void discover_schemas_and_tables_delegate() {
        var adapter = new PluginConnectorAdapter(
                pluginWith("x", "UP", capsAll(), List.of()),
                desc("p", "x"));
        assertEquals(List.of("public"), adapter.discoverSchemas(ctx()));
        assertEquals(List.of("t"), adapter.discoverTables(ctx(), "public"));
    }

    @Test
    void fetchColumns_wraps_plugin_columns() {
        var adapter = new PluginConnectorAdapter(
                pluginWith("x", "UP", capsAll(),
                        List.of(Map.of("name", "id", "type", "int", "primaryKey", true))),
                desc("p", "x"));
        var cols = adapter.fetchColumns(ctx(), "public", "t");
        assertEquals(1, cols.size());
        assertEquals("id", cols.get(0).name());
        assertTrue(cols.get(0).primaryKey());
    }

    @Test
    void primary_key_metadata_built_from_pk_flag() {
        var adapter = new PluginConnectorAdapter(
                pluginWith("x", "UP", capsAll(),
                        List.of(Map.of("name", "id", "type", "int", "primaryKey", true))),
                desc("p", "x"));
        var pk = adapter.fetchPrimaryKey(ctx(), "public", "t");
        assertEquals(List.of("id"), pk.columnNames());
    }

    @Test
    void rangeChunks_returns_single_whole_chunk() {
        var adapter = new PluginConnectorAdapter(
                pluginWith("x", "UP", capsAll(), List.of()),
                desc("p", "x"));
        var chunks = adapter.rangeChunks(ctx(), "public", "t", 8);
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).isWhole());
    }

    @Test
    void estimateRows_returns_minus_one_when_no_snapshot_provider() {
        var adapter = new PluginConnectorAdapter(
                pluginWith("x", "UP", capsNone(), List.of()),
                desc("p", "x"));
        assertEquals(-1L, adapter.estimateRows(ctx(), "public", "t"));
    }

    @Test
    void readBatch_round_trips_cursor() {
        var calls = new java.util.ArrayList<Integer>();
        SnapshotProvider sp = new SnapshotProvider() {

            @Override
            public long estimateRowCount(PluginContext c, String s, String t) {
                return 100;
            }
            @Override
            public PageResult readBatch(PluginContext c, String s, String t, int batchNumber, int batchSize) {
                calls.add(batchNumber);
                if (calls.size() == 1) {
                    return new PageResult(List.of(Map.of("id", 1)), "next");
                }
                return new PageResult(List.of(), null);
            }
        };
        var combined = new CombinedConnector(
                pluginWith("x", "UP", capsNone(), List.of()), sp, null);
        var adapter = new PluginConnectorAdapter(combined, desc("p", "x"));

        // First call: cursor=null → batch 0
        var page1 = adapter.readBatch(ctx(), "public", "t",
                new BatchInformation(0, 100, "t", null));
        assertEquals(1, page1.rows().size());
        assertNotNull(page1.nextCursor());
        // Second call: cursor from page1 → batch 1
        var page2 = adapter.readBatch(ctx(), "public", "t",
                new BatchInformation(1, 100, "t", page1.nextCursor()));
        assertEquals(0, page2.rows().size());
        assertNull(page2.nextCursor());
        assertEquals(List.of(0, 1), calls);
    }

    @Test
    void cdc_event_synthesis_populates_all_fields() {
        CdcProvider cp = new CdcProvider() {

            @Override
            public void startCapture(PluginContext c, Consumer<CdcEvent> consumer) {
                consumer.accept(new CdcEvent("evt-1", "INSERT", "public", "t",
                        null, Map.of("id", 1), Map.of("lsn", "0/16")));
            }
            @Override
            public void stopCapture() {
            }
            @Override
            public boolean isCapturing() {
                return true;
            }
            @Override
            public Map<String, String> currentOffset() {
                return Map.of("lsn", "0/16");
            }
        };
        var combined = new CombinedConnector(
                pluginWith("kafka", "UP", capsAll(), List.of()), null, cp);
        var adapter = new PluginConnectorAdapter(combined, desc("plugin-x", "kafka"));
        var got = new java.util.ArrayList<CDCEvent>();
        adapter.startCDC(ctx(), got::add);
        assertEquals(1, got.size());
        var event = got.get(0);
        assertEquals("evt-1", event.header().eventId());
        assertEquals("plugin:plugin-x", event.header().pipelineId());
        assertEquals(CDCOperation.INSERT, event.operation());
        assertEquals("public", event.source().schema());
        assertEquals("t", event.source().table());
        assertEquals("kafka", event.source().connectorType());
        assertEquals(Map.of("id", 1), event.payload().after());
        assertNotNull(event.metadata().capturedAt());
        assertNull(event.transaction());
        assertEquals("0/16", event.offset().offset().get("lsn"));
    }

    @Test
    void cdc_start_throws_when_plugin_not_cdc_provider() {
        var adapter = new PluginConnectorAdapter(
                pluginWith("x", "UP", capsNone(), List.of()),
                desc("p", "x"));
        assertThrows(UnsupportedOperationException.class,
                () -> adapter.startCDC(ctx(), e -> {
                }));
    }

    @Test
    void cdc_capture_status_reports_running_when_capturing() {
        CdcProvider cp = new CdcProvider() {

            @Override
            public void startCapture(PluginContext c, Consumer<CdcEvent> consumer) {
            }
            @Override
            public void stopCapture() {
            }
            @Override
            public boolean isCapturing() {
                return true;
            }
            @Override
            public Map<String, String> currentOffset() {
                return Map.of();
            }
        };
        var combined = new CombinedConnector(
                pluginWith("x", "UP", capsAll(), List.of()), null, cp);
        var adapter = new PluginConnectorAdapter(combined, desc("p", "x"));
        adapter.startCDC(ctx(), e -> {
        });
        assertEquals(CaptureStatus.RUNNING, adapter.captureStatus());
        adapter.stopCDC();
        assertEquals(CaptureStatus.INACTIVE, adapter.captureStatus());
    }

    @Test
    void cdc_event_operation_normalization() {
        for (var op : new String[]{"insert", "UPDATE", "Delete", "create", "i", "u", "d", "weird"}) {
            CDCOperation result = PluginCdcEventAdapter.parseOperation(op);
            CDCOperation expected = switch (op.toLowerCase()) {
                case "insert", "create", "i" -> CDCOperation.INSERT;
                case "update", "u" -> CDCOperation.UPDATE;
                case "delete", "d" -> CDCOperation.DELETE;
                default -> CDCOperation.READ;
            };
            assertEquals(expected, result, "op=" + op);
        }
    }

    @Test
    void cursor_codec_round_trip() {
        assertEquals("b:0", PluginCursorCodec.encode(0));
        assertEquals(0, PluginCursorCodec.decode(null));
        assertEquals(0, PluginCursorCodec.decode(""));
        assertEquals(5, PluginCursorCodec.decode("b:5"));
        // Unknown format starts at 0.
        assertEquals(0, PluginCursorCodec.decode("garbage"));
    }

    @Test
    void type_resolver_handles_null_and_blank() {
        assertEquals(ConnectorType.GENERIC_PLUGIN, ConnectorTypeResolver.resolve(null));
        assertEquals(ConnectorType.GENERIC_PLUGIN, ConnectorTypeResolver.resolve(""));
        assertEquals(ConnectorType.MONGODB, ConnectorTypeResolver.resolve("mongo"));
        assertEquals(ConnectorType.REDIS, ConnectorTypeResolver.resolve("REDIS"));
        assertEquals(ConnectorType.GENERIC_PLUGIN, ConnectorTypeResolver.resolve("rocket-db"));
    }

    /**
     * Combines a PluginConnector with optional SnapshotProvider and/or CdcProvider
     * for testing.
     */
    private static final class CombinedConnector implements PluginConnector, SnapshotProvider, CdcProvider {

        private final PluginConnector base;
        private final SnapshotProvider sp;
        private final CdcProvider cp;

        CombinedConnector(PluginConnector base, SnapshotProvider sp, CdcProvider cp) {
            this.base = base;
            this.sp = sp;
            this.cp = cp;
        }

        @Override
        public PluginDescriptor descriptor() {
            return base.descriptor();
        }
        @Override
        public com.syncflow.plugin.capabilities.ConnectorCapabilities capabilities() {
            return base.capabilities();
        }
        @Override
        public String health() {
            return base.health();
        }
        @Override
        public Map<String, String> metadata() {
            return base.metadata();
        }
        @Override
        public List<String> discoverSchemas(PluginContext c) {
            return base.discoverSchemas(c);
        }
        @Override
        public List<String> discoverTables(PluginContext c, String s) {
            return base.discoverTables(c, s);
        }
        @Override
        public List<Map<String, Object>> discoverColumns(PluginContext c, String s, String t) {
            return base.discoverColumns(c, s, t);
        }
        @Override
        public long estimateRowCount(PluginContext c, String s, String t) {
            return sp == null ? -1 : sp.estimateRowCount(c, s, t);
        }
        @Override
        public PageResult readBatch(PluginContext c, String s, String t, int bn, int bs) {
            return sp == null ? PageResult.empty() : sp.readBatch(c, s, t, bn, bs);
        }
        @Override
        public void startCapture(PluginContext c, Consumer<CdcEvent> consumer) {
            if (cp != null)
                cp.startCapture(c, consumer);
        }
        @Override
        public void stopCapture() {
            if (cp != null)
                cp.stopCapture();
        }
        @Override
        public boolean isCapturing() {
            return cp != null && cp.isCapturing();
        }
        @Override
        public Map<String, String> currentOffset() {
            return cp == null ? Map.of() : cp.currentOffset();
        }
    }
}
