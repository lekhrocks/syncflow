package com.syncflow.api.pipeline.mapper;

import com.syncflow.api.pipeline.entity.PipelineDesignEntity;
import com.syncflow.core.pipeline.AuditInformation;
import com.syncflow.core.pipeline.PipelineDesign;
import com.syncflow.core.pipeline.PipelineId;
import com.syncflow.core.pipeline.PipelineName;
import com.syncflow.core.pipeline.PipelineStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Maps between {@link PipelineDesign} and {@link PipelineDesignEntity}.
 * JSON serialization of JSONB fields is delegated to {@link JsonMapper}.
 */
@Mapper(componentModel = "spring", uses = JsonMapper.class)
public interface PipelineDesignEntityMapper {

    @Mapping(target = "id", expression = "java(design.id().value())")
    @Mapping(target = "name", expression = "java(design.name().value())")
    @Mapping(target = "status", expression = "java(design.status().name())")
    @Mapping(target = "source", expression = "java(jsonMapper.fromSourceReference(design.source()))")
    @Mapping(target = "destination", expression = "java(jsonMapper.fromDestinationReference(design.destination()))")
    @Mapping(target = "tableMappings", expression = "java(jsonMapper.fromTableMappings(design.tableMappings()))")
    @Mapping(target = "settings", expression = "java(jsonMapper.fromPipelineSettings(design.settings()))")
    @Mapping(target = "version", expression = "java(design.audit().version())")
    @Mapping(target = "createdBy", expression = "java(design.audit().createdBy())")
    @Mapping(target = "createdAt", expression = "java(design.audit().createdAt())")
    @Mapping(target = "updatedAt", expression = "java(design.audit().updatedAt())")
    @Mapping(target = "versions", ignore = true)
    PipelineDesignEntity toEntity(PipelineDesign design,
            @org.mapstruct.Context JsonMapper jsonMapper);

    /**
     * Update an existing entity in-place (used for updates and rollbacks).
     * Does not touch id or createdAt.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "versions", ignore = true)
    @Mapping(target = "name", expression = "java(design.name().value())")
    @Mapping(target = "status", expression = "java(design.status().name())")
    @Mapping(target = "source", expression = "java(jsonMapper.fromSourceReference(design.source()))")
    @Mapping(target = "destination", expression = "java(jsonMapper.fromDestinationReference(design.destination()))")
    @Mapping(target = "tableMappings", expression = "java(jsonMapper.fromTableMappings(design.tableMappings()))")
    @Mapping(target = "settings", expression = "java(jsonMapper.fromPipelineSettings(design.settings()))")
    @Mapping(target = "version", expression = "java(design.audit().version())")
    @Mapping(target = "createdBy", expression = "java(design.audit().createdBy())")
    @Mapping(target = "updatedAt", expression = "java(design.audit().updatedAt())")
    void updateEntity(@MappingTarget PipelineDesignEntity entity, PipelineDesign design,
            @org.mapstruct.Context JsonMapper jsonMapper);

    default PipelineDesign toDomain(PipelineDesignEntity e,
            @org.mapstruct.Context JsonMapper jsonMapper) {
        var source = jsonMapper.toSourceReference(e.getSource());
        var destination = jsonMapper.toDestinationReference(e.getDestination());
        var mappings = jsonMapper.toTableMappings(e.getTableMappings());
        var settings = jsonMapper.toPipelineSettings(e.getSettings());
        var audit = new AuditInformation(e.getVersion(), e.getCreatedAt(),
                e.getUpdatedAt(), e.getCreatedBy());
        return new PipelineDesign(
                PipelineId.from(e.getId()),
                new PipelineName(e.getName()),
                PipelineStatus.valueOf(e.getStatus()),
                source, destination, mappings, settings, audit);
    }
}
