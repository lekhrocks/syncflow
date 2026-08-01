package com.syncflow.core.cdc;

import java.util.Map;

public record EventPayload(
        Map<String, Object> before,
        Map<String, Object> after,
        Map<String, Object> primaryKeys) {

    public EventPayload {
        before = before == null ? null : Map.copyOf(before);
        after = after == null ? null : Map.copyOf(after);
        primaryKeys = Map.copyOf(primaryKeys == null ? Map.of() : primaryKeys);
    }
}
