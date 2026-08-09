package com.syncflow.core.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Setter
@Getter
public class Pipeline {

    private String id;
    private @NotBlank String name;
    private @NotNull PipelineStatus status;
    private @NotNull @Valid ConnectionConfiguration source;
    private @NotNull @Valid ConnectionConfiguration destination;
    private @Valid TransformationConfiguration mapping;
    private Instant createdAt;
    private Instant updatedAt;

    public Pipeline() {
    }

    public Pipeline(String name, ConnectionConfiguration source,
            ConnectionConfiguration destination,
            TransformationConfiguration mapping) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.status = PipelineStatus.CREATED;
        this.source = source;
        this.destination = destination;
        this.mapping = mapping;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

}
