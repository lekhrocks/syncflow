package com.syncflow.api.pipeline.mapper;

import com.syncflow.api.pipeline.entity.PipelineEntity;
import com.syncflow.core.model.Pipeline;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps between the core {@link Pipeline} domain model and
 * {@link PipelineEntity}.
 * JSON serialization of JSONB fields is delegated to {@link JsonMapper}.
 * id, name, createdAt and updatedAt are plain String/Instant fields — mapped
 * directly.
 */
@Mapper(componentModel = "spring")
public interface PipelineEntityMapper {

    @Mapping(target = "id", source = "domain.id")
    @Mapping(target = "name", source = "domain.name")
    @Mapping(target = "status", expression = "java(domain.getStatus().name())")
    @Mapping(target = "source", expression = "java(jsonMapper.fromConnectionConfiguration(domain.getSource()))")
    @Mapping(target = "destination", expression = "java(jsonMapper.fromConnectionConfiguration(domain.getDestination()))")
    @Mapping(target = "mapping", expression = "java(jsonMapper.fromTransformationConfiguration(domain.getMapping()))")
    @Mapping(target = "createdAt", source = "domain.createdAt")
    @Mapping(target = "updatedAt", source = "domain.updatedAt")
    PipelineEntity toEntity(Pipeline domain, @Context JsonMapper jsonMapper);

    @Mapping(target = "id", source = "entity.id")
    @Mapping(target = "name", source = "entity.name")
    @Mapping(target = "status", expression = "java(com.syncflow.core.model.PipelineStatus.valueOf(entity.getStatus()))")
    @Mapping(target = "source", expression = "java(jsonMapper.toConnectionConfiguration(entity.getSource()))")
    @Mapping(target = "destination", expression = "java(jsonMapper.toConnectionConfiguration(entity.getDestination()))")
    @Mapping(target = "mapping", expression = "java(jsonMapper.toTransformationConfiguration(entity.getMapping()))")
    @Mapping(target = "createdAt", source = "entity.createdAt")
    @Mapping(target = "updatedAt", source = "entity.updatedAt")
    Pipeline toDomain(PipelineEntity entity, @Context JsonMapper jsonMapper);
}
