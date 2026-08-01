package com.syncflow.api.dto;

import com.syncflow.core.model.ConnectionConfiguration;
import com.syncflow.core.model.TransformationConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdatePipelineRequest(
        @NotBlank String name,
        @NotNull @Valid ConnectionConfiguration source,
        @NotNull @Valid ConnectionConfiguration destination,
        TransformationConfiguration mapping) {
}
