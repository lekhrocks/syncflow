package com.syncflow.core.pipeline.mapping;

import com.syncflow.core.pipeline.transform.TransformationRule;
import java.util.List;

public record FieldMapping(
        String sourceField,
        String destinationField,
        List<TransformationRule> transformations) {

    public FieldMapping {
        transformations = List.copyOf(transformations == null ? List.of() : transformations);
    }
}
