package com.syncflow.core.metadata;

import java.util.List;

public record MetadataResponse<T>(
        String connectionId,
        String type,
        List<T> data,
        int totalCount,
        long discoveryTimeMs,
        boolean cached,
        String error) {

    public static <T> MetadataResponse<T> of(String connectionId, String type,
            List<T> data, long timeMs, boolean cached) {
        return new MetadataResponse<>(connectionId, type, data, data.size(), timeMs, cached, null);
    }

    public static <T> MetadataResponse<T> error(String connectionId, String type, String error) {
        return new MetadataResponse<>(connectionId, type, List.of(), 0, 0, false, error);
    }
}
