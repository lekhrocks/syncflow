package com.syncflow.core.governance;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class DataGovernanceService {

    private final Map<String, SchemaVersion> schemaHistory = new ConcurrentHashMap<>();
    private final Map<String, DataLineageRecord> lineageStore = new ConcurrentHashMap<>();
    private final AtomicLong counter = new AtomicLong(0);

    public void recordSchemaChange(String connectionId, String schema, String table,
            String columnsJson, String changeSummary, String ddl) {
        var id = "schema-" + counter.incrementAndGet();
        var prev = schemaHistory.values().stream()
                .filter(s -> s.connectionId().equals(connectionId) && s.table().equals(table))
                .max(Comparator.comparingInt(SchemaVersion::version))
                .orElse(null);
        var version = prev != null ? prev.version() + 1 : 1;
        var record = new SchemaVersion(id, connectionId, schema, table, columnsJson,
                version, changeSummary, ddl, Instant.now());
        schemaHistory.put(id, record);
    }

    public void recordLineage(String pipelineId, String sourceConn, String sourceSchema,
            String sourceTable, String sourceCols,
            String destConn, String destSchema, String destTable,
            String destCols, String transformSummary, long rows) {
        var id = "lineage-" + counter.incrementAndGet();
        var record = new DataLineageRecord(id, pipelineId, sourceConn, sourceSchema,
                sourceTable, sourceCols, destConn, destSchema, destTable,
                destCols, transformSummary, rows, Instant.now());
        lineageStore.put(id, record);
    }

    public List<SchemaVersion> schemaHistory(String connectionId, String table) {
        return schemaHistory.values().stream()
                .filter(s -> s.connectionId().equals(connectionId))
                .filter(s -> table == null || s.table().equals(table))
                .sorted(Comparator.comparingInt(SchemaVersion::version))
                .toList();
    }

    public List<DataLineageRecord> lineage(String pipelineId) {
        return lineageStore.values().stream()
                .filter(l -> pipelineId == null || l.pipelineId().equals(pipelineId))
                .sorted(Comparator.comparing(DataLineageRecord::timestamp).reversed())
                .toList();
    }

    public List<ColumnTag> classifyColumns(String tableName, List<String> columns) {
        var tags = new ArrayList<ColumnTag>();
        for (var col : columns) {
            var lower = col.toLowerCase();
            if (lower.contains("email"))
                tags.add(ColumnTag.PII_EMAIL);
            else if (lower.contains("phone") || lower.contains("mobile"))
                tags.add(ColumnTag.PII_PHONE);
            else if (lower.contains("ssn") || lower.contains("social"))
                tags.add(ColumnTag.PII_SSN);
            else if (lower.contains("password") || lower.contains("secret") || lower.contains("token"))
                tags.add(ColumnTag.CREDENTIAL);
            else if (lower.contains("first_name") || lower.contains("last_name") || lower.contains("full_name"))
                tags.add(ColumnTag.PII_NAME);
            else if (lower.contains("address") || lower.contains("street") || lower.contains("zip"))
                tags.add(ColumnTag.PII_ADDRESS);
            else if (lower.contains("dob") || lower.contains("birth"))
                tags.add(ColumnTag.PII_DOB);
            else if (lower.contains("salary") || lower.contains("account") || lower.contains("credit"))
                tags.add(ColumnTag.FINANCIAL);
            else if (lower.contains("diagnosis") || lower.contains("treatment"))
                tags.add(ColumnTag.HEALTH);
        }
        return tags;
    }

    public boolean isSensitive(ColumnTag tag) {
        return switch (tag) {
            case PII_EMAIL, PII_PHONE, PII_SSN, PII_NAME, PII_ADDRESS, PII_DOB,
                    CREDENTIAL, FINANCIAL, HEALTH ->
                true;
            case INTERNAL_ONLY -> false;
        };
    }

    public DataClassification classifyTable(List<ColumnTag> columnTags) {
        if (columnTags.stream().anyMatch(t -> t == ColumnTag.CREDENTIAL || t == ColumnTag.PII_SSN))
            return DataClassification.RESTRICTED;
        if (columnTags.stream().anyMatch(this::isSensitive))
            return DataClassification.CONFIDENTIAL;
        return DataClassification.INTERNAL;
    }
}
