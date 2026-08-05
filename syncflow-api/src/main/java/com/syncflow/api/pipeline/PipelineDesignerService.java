package com.syncflow.api.pipeline;

import com.syncflow.api.metadata.MetadataDiscoveryService;
import com.syncflow.api.pipeline.entity.PipelineDesignEntity;
import com.syncflow.api.pipeline.entity.PipelineDesignVersionEntity;
import com.syncflow.api.pipeline.mapper.JsonMapper;
import com.syncflow.api.pipeline.mapper.PipelineDesignEntityMapper;
import com.syncflow.api.pipeline.repository.PipelineDesignJpaRepository;
import com.syncflow.api.pipeline.repository.PipelineDesignVersionJpaRepository;
import com.syncflow.core.pipeline.AuditInformation;
import com.syncflow.core.pipeline.DestinationReference;
import com.syncflow.core.pipeline.PipelineDesign;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class PipelineDesignerService {

    private final PipelineDesignJpaRepository designRepo;
    private final PipelineDesignVersionJpaRepository versionRepo;
    private final PipelineDesignEntityMapper mapper;
    private final JsonMapper jsonMapper;
    private final PipelineValidator validator;
    private final MetadataDiscoveryService metadataService;

    public PipelineDesignerService(PipelineDesignJpaRepository designRepo,
            PipelineDesignVersionJpaRepository versionRepo,
            PipelineDesignEntityMapper mapper,
            JsonMapper jsonMapper,
            PipelineValidator validator,
            MetadataDiscoveryService metadataService) {
        this.designRepo = designRepo;
        this.versionRepo = versionRepo;
        this.mapper = mapper;
        this.jsonMapper = jsonMapper;
        this.validator = validator;
        this.metadataService = metadataService;
    }

    public PipelineDesign create(PipelineName name, SourceReference source,
            DestinationReference destination,
            List<TableMapping> mappings,
            PipelineSettings settings) {
        var design = PipelineDesign.create(name, source, destination, mappings, settings);
        var entity = mapper.toEntity(design, jsonMapper);
        designRepo.save(entity);
        saveVersion(entity, design);
        return design;
    }

    @Transactional(readOnly = true)
    public PipelineDesign get(String id) {
        return designRepo.findById(id)
                .map(e -> mapper.toDomain(e, jsonMapper))
                .orElseThrow(() -> new NoSuchElementException("Pipeline not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<PipelineDesign> list() {
        return designRepo.findAll().stream()
                .map(e -> mapper.toDomain(e, jsonMapper))
                .toList();
    }

    @Transactional
    public PipelineDesign update(String id, PipelineName name,
            SourceReference source,
            DestinationReference destination,
            List<TableMapping> mappings,
            PipelineSettings settings) {
        var entity = designRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Pipeline not found: " + id));
        var existing = mapper.toDomain(entity, jsonMapper);
        var now = Instant.now();
        var updated = new PipelineDesign(
                existing.id(), name, existing.status(),
                source, destination, mappings, settings,
                new AuditInformation(existing.audit().version() + 1,
                        existing.audit().createdAt(), now, existing.audit().createdBy()));
        mapper.updateEntity(entity, updated, jsonMapper);
        designRepo.save(entity);
        saveVersion(entity, updated);
        return updated;
    }

    @Transactional
    public void delete(String id) {
        if (!designRepo.existsById(id)) {
            throw new NoSuchElementException("Pipeline not found: " + id);
        }
        designRepo.deleteById(id);
    }

    public ValidationResult validate(String id) {
        // No @Transactional: the validator probes connections/metadata which may throw
        // and catch internally. A shared read-only txn would get marked rollback-only
        // by those throws and fail on commit with UnexpectedRollbackException.
        return validator.validate(get(id));
    }

    @Transactional(readOnly = true)
    public List<PipelineDesign> versions(String id) {
        return versionRepo.findByPipelineIdOrderByVersionAsc(id).stream()
                .map(v -> jsonMapper.fromJson(v.getSnapshot(), PipelineDesign.class))
                .toList();
    }

    @Transactional
    public PipelineDesign rollback(String id, int targetVersion) {
        var versionEntity = versionRepo.findByPipelineIdAndVersion(id, targetVersion)
                .orElseThrow(() -> new NoSuchElementException(
                        "Version " + targetVersion + " not found for pipeline: " + id));
        var target = jsonMapper.fromJson(versionEntity.getSnapshot(), PipelineDesign.class);
        var entity = designRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Pipeline not found: " + id));
        var now = Instant.now();
        var rolled = new PipelineDesign(target.id(), target.name(), target.status(),
                target.source(), target.destination(), target.tableMappings(), target.settings(),
                new AuditInformation(entity.getVersion(), target.audit().createdAt(), now,
                        target.audit().createdBy()));
        mapper.updateEntity(entity, rolled, jsonMapper);
        designRepo.save(entity);
        return rolled;
    }

    @Transactional(readOnly = true)
    public PipelinePreview preview(String id) {
        var design = get(id);

        // Return an empty preview when no table mappings have been defined yet
        if (design.tableMappings().isEmpty()) {
            return new PipelinePreview("", "", List.of(), List.of(), List.of(), List.of(), 0);
        }

        var tm = design.tableMappings().getFirst();

        var srcCols = new ArrayList<PreviewColumn>();
        var destCols = new ArrayList<PreviewColumn>();
        var transforms = new ArrayList<PreviewTransformation>();
        var filters = new ArrayList<PreviewFilter>();

        try {
            var srcColumns = metadataService.discoverColumns(
                    design.source().connectionId(),
                    design.source().schema(), tm.sourceTable());
            for (var col : srcColumns.data()) {
                srcCols.add(new PreviewColumn(col.name(), col.dataType().jdbcType(),
                        col.dataType().jdbcType(), false, false));
            }
        } catch (Exception e) {
            srcCols.add(new PreviewColumn("(error: " + e.getMessage() + ")", "", "", false, false));
        }

        for (var cm : tm.columnMappings()) {
            destCols.add(new PreviewColumn(cm.destinationColumn(), "", "",
                    !cm.transformations().isEmpty(), false));
            for (var tr : cm.transformations()) {
                transforms.add(new PreviewTransformation(cm.sourceColumn(), cm.destinationColumn(),
                        tr.type().name(), summarizeTransform(tr)));
            }
        }

        if (tm.filter() != null) {
            for (var c : tm.filter().conditions()) {
                filters.add(new PreviewFilter(c.field(), c.operator().name(),
                        c.values().isEmpty() ? "" : String.join(",", c.values())));
            }
        }

        return new PipelinePreview(tm.sourceTable(),
                tm.destinationTable() != null ? tm.destinationTable() : tm.destinationCollection(),
                srcCols, destCols, transforms, filters, destCols.size());
    }

    @Transactional(readOnly = true)
    public ConflictReport detectConflicts(String id) {
        var issues = validator.validate(get(id)).issues();
        var conflicts = issues.stream()
                .filter(i -> i.severity() == com.syncflow.core.pipeline.validation.ValidationIssue.Severity.ERROR)
                .map(i -> new ConflictReport.ConflictItem(i.code(), i.field(), "", i.message()))
                .toList();
        return conflicts.isEmpty() ? ConflictReport.clear() : new ConflictReport(true, conflicts);
    }

    // ── Version snapshot ─────────────────────────────────────────────────────

    private void saveVersion(PipelineDesignEntity entity, PipelineDesign design) {
        var v = new PipelineDesignVersionEntity();
        v.setPipeline(entity);
        v.setVersion(design.audit().version());
        v.setSnapshot(jsonMapper.toJson(design));
        v.setSavedAt(Instant.now());
        versionRepo.save(v);
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
