package com.syncflow.core.cdc;

import java.util.Map;

public record EventHeader(
        String eventId,
        String pipelineId,
        String connectionId,
        long eventNumber,
        int version,
        Map<String, String> customProperties) {

    public EventHeader {
        customProperties = Map.copyOf(customProperties == null ? Map.of() : customProperties);
    }
}
