package com.syncflow.plugin.spi;

import java.util.Map;
import java.util.function.Consumer;

public interface CdcProvider {

    void startCapture(PluginContext context, Consumer<CdcEvent> consumer);

    void stopCapture();

    boolean isCapturing();

    Map<String, String> currentOffset();

    record CdcEvent(
            String eventId,
            String operation,
            String schema,
            String table,
            Map<String, Object> before,
            Map<String, Object> after,
            Map<String, String> offset) {
    }
}
