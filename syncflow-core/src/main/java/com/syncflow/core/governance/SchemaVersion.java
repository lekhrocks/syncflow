package com.syncflow.core.governance;

import java.time.Instant;

public record SchemaVersion(
        String id,
        String connectionId,
        String schema,
        String table,
        String columnsJson,
        int version,
        String changeSummary,
        String ddlStatement,
        Instant detectedAt) {
}
