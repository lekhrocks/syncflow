package com.syncflow.api.pipeline;

import com.syncflow.api.connection.service.ConnectionService;
import com.syncflow.api.metadata.MetadataDiscoveryService;
import com.syncflow.core.metadata.ColumnMetadata;
import com.syncflow.core.pipeline.PipelineDesign;
import com.syncflow.core.pipeline.transform.TransformType;
import com.syncflow.core.pipeline.validation.ValidationIssue;
import com.syncflow.core.pipeline.validation.ValidationResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Component
public class PipelineValidator {

    private final MetadataDiscoveryService metadataService;
    private final ConnectionService connectionService;

    public PipelineValidator(MetadataDiscoveryService metadataService,
            ConnectionService connectionService) {
        this.metadataService = metadataService;
        this.connectionService = connectionService;
    }

    public ValidationResult validate(PipelineDesign pipeline) {
        var issues = new ArrayList<ValidationIssue>();
        issues.addAll(validateConnections(pipeline));
        issues.addAll(validateSourceTable(pipeline));
        issues.addAll(validateDestinationTable(pipeline));
        issues.addAll(validateMappings(pipeline));
        issues.addAll(validateConflicts(pipeline));
        issues.addAll(validateTransforms(pipeline));
        var hasErrors = issues.stream().anyMatch(i -> i.severity() == ValidationIssue.Severity.ERROR);
        return new ValidationResult(!hasErrors, issues);
    }

    private List<ValidationIssue> validateConnections(PipelineDesign pipeline) {
        var issues = new ArrayList<ValidationIssue>();
        try {
            connectionService.get(pipeline.source().connectionId());
        } catch (Exception e) {
            issues.add(new ValidationIssue("SOURCE_CONNECTION_NOT_FOUND", "source.connectionId",
                    "Source connection not found: " + pipeline.source().connectionId(),
                    ValidationIssue.Severity.ERROR));
        }
        try {
            connectionService.get(pipeline.destination().connectionId());
        } catch (Exception e) {
            issues.add(new ValidationIssue("DEST_CONNECTION_NOT_FOUND", "destination.connectionId",
                    "Destination connection not found: " + pipeline.destination().connectionId(),
                    ValidationIssue.Severity.ERROR));
        }
        return issues;
    }

    private List<ValidationIssue> validateSourceTable(PipelineDesign pipeline) {
        var issues = new ArrayList<ValidationIssue>();
        var src = pipeline.source();
        try {
            var tables = metadataService.discoverTables(src.connectionId(), src.schema());
            if (tables.error() != null) {
                issues.add(new ValidationIssue("SOURCE_METADATA_ERROR", "source.tableOrCollection",
                        "Cannot read source metadata: " + tables.error(),
                        ValidationIssue.Severity.ERROR));
                return issues;
            }
            for (var m : pipeline.tableMappings()) {
                var found = tables.data().stream().anyMatch(t -> t.name().equals(m.sourceTable()));
                if (!found) {
                    issues.add(new ValidationIssue("SOURCE_TABLE_NOT_FOUND", "mapping.sourceTable",
                            "Source table not found: " + m.sourceTable(),
                            ValidationIssue.Severity.ERROR));
                }
            }
        } catch (Exception e) {
            issues.add(new ValidationIssue("SOURCE_METADATA_FAILED", "source",
                    "Source metadata discovery failed: " + e.getMessage(),
                    ValidationIssue.Severity.ERROR));
        }
        return issues;
    }

    private List<ValidationIssue> validateDestinationTable(PipelineDesign pipeline) {
        var issues = new ArrayList<ValidationIssue>();
        try {
            var tables = metadataService.discoverTables(
                    pipeline.destination().connectionId(), pipeline.destination().schema());
            if (tables.error() != null) {
                issues.add(new ValidationIssue("DEST_METADATA_ERROR", "destination.tableOrCollection",
                        "Cannot read destination metadata: " + tables.error(),
                        ValidationIssue.Severity.WARNING));
            }
        } catch (Exception e) {
            issues.add(new ValidationIssue("DEST_METADATA_FAILED", "destination",
                    "Destination metadata discovery failed: " + e.getMessage(),
                    ValidationIssue.Severity.WARNING));
        }
        return issues;
    }

    private List<ValidationIssue> validateMappings(PipelineDesign pipeline) {
        var issues = new ArrayList<ValidationIssue>();
        var seenSrcCols = new HashSet<String>();
        var seenDestCols = new HashSet<String>();

        for (var tm : pipeline.tableMappings()) {
            for (var cm : tm.columnMappings()) {
                if (!seenSrcCols.add(cm.sourceColumn())) {
                    issues.add(new ValidationIssue("DUPLICATE_SOURCE_COLUMN", "mapping.columnMapping",
                            "Duplicate source column mapping: " + cm.sourceColumn(),
                            ValidationIssue.Severity.ERROR));
                }
                if (!seenDestCols.add(cm.destinationColumn())) {
                    issues.add(new ValidationIssue("DUPLICATE_DEST_COLUMN", "mapping.columnMapping",
                            "Duplicate destination column: " + cm.destinationColumn(),
                            ValidationIssue.Severity.ERROR));
                }
            }
            if (tm.primaryKey() != null && tm.primaryKey().sourceColumns().isEmpty()) {
                issues.add(new ValidationIssue("EMPTY_PRIMARY_KEY", "mapping.primaryKey",
                        "Primary key mapping has no columns", ValidationIssue.Severity.ERROR));
            }
            try {
                var columns = metadataService.discoverColumns(
                        pipeline.source().connectionId(),
                        pipeline.source().schema(), tm.sourceTable());
                var srcColNames = columns.data().stream().map(ColumnMetadata::name).toList();
                for (var cm : tm.columnMappings()) {
                    if (!srcColNames.contains(cm.sourceColumn())) {
                        issues.add(new ValidationIssue("UNKNOWN_SOURCE_COLUMN", "mapping.columnMapping",
                                "Unknown source column: " + cm.sourceColumn(),
                                ValidationIssue.Severity.ERROR));
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return issues;
    }

    private List<ValidationIssue> validateConflicts(PipelineDesign pipeline) {
        var issues = new ArrayList<ValidationIssue>();
        for (var tm : pipeline.tableMappings()) {
            if (tm.primaryKey() == null || tm.primaryKey().sourceColumns().isEmpty()) {
                issues.add(new ValidationIssue("MISSING_PRIMARY_KEY", "mapping.primaryKey",
                        "Table " + tm.sourceTable() + " has no primary key mapping",
                        ValidationIssue.Severity.WARNING));
            }
        }
        return issues;
    }

    private List<ValidationIssue> validateTransforms(PipelineDesign pipeline) {
        var issues = new ArrayList<ValidationIssue>();
        for (var tm : pipeline.tableMappings()) {
            for (var cm : tm.columnMappings()) {
                for (var tr : cm.transformations()) {
                    if (tr.type() == TransformType.CONCATENATE && tr.sourceFields().isEmpty()) {
                        issues.add(new ValidationIssue("EMPTY_CONCAT_SOURCES", "transformation",
                                "CONCATENATE requires at least one source field",
                                ValidationIssue.Severity.ERROR));
                    }
                    if (tr.type() == TransformType.SUBSTRING && !tr.parameters().containsKey("start")) {
                        issues.add(new ValidationIssue("MISSING_SUBSTRING_PARAMS", "transformation",
                                "SUBSTRING requires 'start' parameter",
                                ValidationIssue.Severity.ERROR));
                    }
                }
            }
        }
        return issues;
    }
}
