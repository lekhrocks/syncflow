package com.syncflow.api.pipeline;

import com.syncflow.api.metadata.MetadataDiscoveryService;
import com.syncflow.core.pipeline.AuditInformation;
import com.syncflow.core.pipeline.DestinationReference;
import com.syncflow.core.pipeline.PipelineDesign;
import com.syncflow.core.pipeline.PipelineId;
import com.syncflow.core.pipeline.PipelineName;
import com.syncflow.core.pipeline.PipelineSettings;
import com.syncflow.core.pipeline.SourceReference;
import com.syncflow.core.pipeline.mapping.TableMapping;
import com.syncflow.core.pipeline.preview.ConflictReport;
import com.syncflow.core.pipeline.preview.PipelinePreview;
import com.syncflow.core.pipeline.preview.PreviewColumn;
import com.syncflow.core.pipeline.preview.PreviewFilter;
import com.syncflow.core.pipeline.preview.PreviewTransformation;
import com.syncflow.core.pipeline.validation.ValidationResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class PipelineDesignerService {

    private final Map<PipelineId, PipelineDesign> store = new ConcurrentHashMap<>();
    private final Map<PipelineId, List<PipelineDesign>> versions = new ConcurrentHashMap<>();
    private final PipelineValidator validator;
    private final MetadataDiscoveryService metadataService;

    public PipelineDesignerService(PipelineValidator validator,
            MetadataDiscoveryService metadataService) {
        this.validator = validator;
        this.metadataService = metadataService;
    }

    public PipelineDesign create(PipelineName name, SourceReference source,
            DestinationReference destination,
            List<TableMapping> mappings,
            PipelineSettings settings) {
        var design = PipelineDesign.create(name, source, destination, mappings, settings);
        store.put(design.id(), design);
        saveVersion(design);
        return design;
    }

    public PipelineDesign get(String id) {
        var pipelineId = PipelineId.from(id);
        var d = store.get(pipelineId);
        if (d == null)
            throw new NoSuchElementException("Pipeline not found: " + id);
        return d;
    }

    public List<PipelineDesign> list() {
        return List.copyOf(store.values());
    }

    public PipelineDesign update(String id, PipelineName name,
            SourceReference source,
            DestinationReference destination,
            List<TableMapping> mappings,
            PipelineSettings settings) {
        var existing = get(id);
        var now = Instant.now();
        var audit = existing.audit();
        var design = new PipelineDesign(
                existing.id(), name, existing.status(),
                source, destination, mappings, settings,
                new AuditInformation(audit.version() + 1, audit.createdAt(), now, audit.createdBy()));
        store.put(design.id(), design);
        saveVersion(design);
        return design;
    }

    public void delete(String id) {
        var pipelineId = PipelineId.from(id);
        if (store.remove(pipelineId) == null) {
            throw new NoSuchElementException("Pipeline not found: " + id);
        }
        versions.remove(pipelineId);
    }

    public ValidationResult validate(String id) {
        var design = get(id);
        return validator.validate(design);
    }

    public List<PipelineDesign> versions(String id) {
        var pipelineId = PipelineId.from(id);
        var v = versions.get(pipelineId);
        return v == null ? List.of() : List.copyOf(v);
    }

    public PipelineDesign rollback(String id, int targetVersion) {
        var pipelineId = PipelineId.from(id);
        var v = versions.get(pipelineId);
        if (v == null || v.isEmpty())
            throw new NoSuchElementException("No versions found");
        var target = v.stream()
                .filter(d -> d.audit().version() == targetVersion)
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Version not found: " + targetVersion));
        var now = Instant.now();
        var currentAudit = target.audit();
        var rolled = new PipelineDesign(target.id(), target.name(), target.status(),
                target.source(), target.destination(),
                target.tableMappings(), target.settings(),
                new AuditInformation(currentAudit.version(), currentAudit.createdAt(), now, currentAudit.createdBy()));
        store.put(rolled.id(), rolled);
        return rolled;
    }

    public PipelinePreview preview(String id) {
        var design = get(id);
        var tm = design.tableMappings().stream().findFirst();
        if (tm.isEmpty())
            throw new IllegalStateException("No table mappings to preview");

        var mapping = tm.get();
        var srcCols = new ArrayList<PreviewColumn>();
        var destCols = new ArrayList<PreviewColumn>();
        var transforms = new ArrayList<PreviewTransformation>();
        var filters = new ArrayList<PreviewFilter>();

        try {
            var srcColumns = metadataService.discoverColumns(
                    design.source().connectionId(),
                    design.source().schema(), mapping.sourceTable());
            for (var col : srcColumns.data()) {
                srcCols.add(new PreviewColumn(col.name(), col.dataType().jdbcType(),
                        col.dataType().jdbcType(), false, false));
            }
        } catch (Exception e) {
            srcCols.add(new PreviewColumn("(error: " + e.getMessage() + ")", "", "", false, false));
        }

        for (var cm : mapping.columnMappings()) {
            destCols.add(new PreviewColumn(cm.destinationColumn(), "", "", !cm.transformations().isEmpty(), false));
            for (var tr : cm.transformations()) {
                transforms.add(new PreviewTransformation(cm.sourceColumn(), cm.destinationColumn(),
                        tr.type().name(), summarizeTransform(tr)));
            }
        }

        if (mapping.filter() != null) {
            for (var c : mapping.filter().conditions()) {
                filters.add(new PreviewFilter(c.field(), c.operator().name(),
                        c.values().isEmpty() ? "" : String.join(",", c.values())));
            }
        }

        return new PipelinePreview(mapping.sourceTable(),
                mapping.destinationTable() != null ? mapping.destinationTable() : mapping.destinationCollection(),
                srcCols, destCols, transforms, filters, destCols.size());
    }

    public ConflictReport detectConflicts(String id) {
        var design = get(id);
        var issues = validator.validate(design).issues();
        var conflicts = issues.stream()
                .filter(i -> i.severity() == com.syncflow.core.pipeline.validation.ValidationIssue.Severity.ERROR)
                .map(i -> new ConflictReport.ConflictItem(i.code(), i.field(), "", i.message()))
                .toList();
        return conflicts.isEmpty()
                ? ConflictReport.clear()
                : new ConflictReport(true, conflicts);
    }

    private void saveVersion(PipelineDesign design) {
        versions.computeIfAbsent(design.id(), k -> new CopyOnWriteArrayList<>())
                .add(design);
    }

    private String summarizeTransform(com.syncflow.core.pipeline.transform.TransformationRule tr) {
        return switch (tr.type()) {
            case RENAME -> "Rename to " + tr.parameters().get("newName");
            case CONSTANT_VALUE -> "Constant: " + tr.parameters().get("value");
            case CONCATENATE -> "Concat with separator '" + tr.parameters().get("separator") + "'";
            case UPPERCASE -> "Convert to uppercase";
            case LOWERCASE -> "Convert to lowercase";
            case TRIM -> "Trim whitespace";
            case DEFAULT_VALUE -> "Default: " + tr.parameters().get("value");
            case EXPRESSION -> "Expression: " + tr.parameters().get("expression");
            case IGNORE -> "Ignored";
            case SUBSTRING -> "Substring(start=" + tr.parameters().get("start") + ")";
        };
    }
}
