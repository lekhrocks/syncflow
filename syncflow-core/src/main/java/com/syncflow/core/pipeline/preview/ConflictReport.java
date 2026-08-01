package com.syncflow.core.pipeline.preview;

import java.util.List;

public record ConflictReport(
        boolean hasConflicts,
        List<ConflictItem> conflicts) {

    public static ConflictReport clear() {
        return new ConflictReport(false, List.of());
    }

    public record ConflictItem(
            String type,
            String source,
            String destination,
            String description) {
    }
}
