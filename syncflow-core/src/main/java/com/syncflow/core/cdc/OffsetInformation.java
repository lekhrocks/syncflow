package com.syncflow.core.cdc;

import java.time.Instant;
import java.util.Map;

public record OffsetInformation(
        String connectorType,
        Map<String, String> offset,
        String transactionId,
        Instant timestamp) {

    public OffsetInformation {
        offset = Map.copyOf(offset == null ? Map.of() : offset);
    }
}
