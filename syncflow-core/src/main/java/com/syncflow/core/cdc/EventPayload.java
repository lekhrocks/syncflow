package com.syncflow.core.cdc;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public record EventPayload(
        Map<String, Object> before,
        Map<String, Object> after,
        Map<String, Object> primaryKeys) {

    public EventPayload {
        // Debezium rows can contain NULL column values; Map.copyOf would throw on them.
        before = copyOrNull(before);
        after = copyOrNull(after);
        primaryKeys = copyOrNull(primaryKeys) == null ? Map.of() : primaryKeys;
    }

    private static Map<String, Object> copyOrNull(Map<String, Object> source) {
        return source == null ? null : Collections.unmodifiableMap(new HashMap<>(source));
    }
}
