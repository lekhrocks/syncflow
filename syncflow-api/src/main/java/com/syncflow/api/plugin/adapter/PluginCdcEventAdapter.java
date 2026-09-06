package com.syncflow.api.plugin.adapter;

import com.syncflow.core.cdc.CDCOperation;
import com.syncflow.core.cdc.CDCEvent;
import com.syncflow.core.cdc.EventHeader;
import com.syncflow.core.cdc.EventMetadata;
import com.syncflow.core.cdc.EventPayload;
import com.syncflow.core.cdc.EventSource;
import com.syncflow.core.cdc.OffsetInformation;
import com.syncflow.plugin.descriptor.PluginDescriptor;
import com.syncflow.plugin.spi.CdcProvider;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/**
 * Converts the flat {@link CdcProvider.CdcEvent} emitted by a plugin into
 * the deeply nested core {@link CDCEvent}. Synthesizes fields the plugin
 * SPI does not provide (pipelineId, connectionId, eventNumber, version,
 * capturedAt, captureLatencyMs, transaction) with sensible defaults.
 */
public final class PluginCdcEventAdapter {

    private PluginCdcEventAdapter() {
    }

    public static CDCEvent toCore(CdcProvider.CdcEvent event, PluginDescriptor descriptor) {
        var eventId = event.eventId() != null
                ? event.eventId()
                : java.util.UUID.randomUUID().toString();
        var now = Instant.now();
        var pipelineId = "plugin:" + descriptor.pluginId();
        var offsetMap = event.offset() == null ? Map.<String, String>of() : event.offset();

        return new CDCEvent(
                new EventHeader(eventId, pipelineId, pipelineId, 0L, 1, Map.of()),
                new EventSource(descriptor.pluginId(), event.schema(), event.table(),
                        descriptor.connectorType()),
                parseOperation(event.operation()),
                new EventPayload(event.before(), event.after(), Map.of()),
                new EventMetadata(0L, now, 0L),
                null,
                new OffsetInformation(descriptor.connectorType(), offsetMap, null, now));
    }

    static CDCOperation parseOperation(String op) {
        if (op == null || op.isBlank()) {
            return CDCOperation.READ;
        }
        return switch (op.trim().toUpperCase(Locale.ROOT)) {
            case "INSERT", "I", "CREATE" -> CDCOperation.INSERT;
            case "UPDATE", "U" -> CDCOperation.UPDATE;
            case "DELETE", "D" -> CDCOperation.DELETE;
            default -> CDCOperation.READ;
        };
    }
}
