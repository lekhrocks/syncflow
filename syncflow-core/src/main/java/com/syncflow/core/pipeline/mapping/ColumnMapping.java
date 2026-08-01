package com.syncflow.core.pipeline.mapping;

import com.syncflow.core.pipeline.transform.TransformationRule;
import java.util.List;

public record ColumnMapping(
        String sourceColumn,
        String destinationColumn,
        List<TransformationRule> transformations) {

    public ColumnMapping {
        transformations = List.copyOf(transformations == null ? List.of() : transformations);
    }
}
