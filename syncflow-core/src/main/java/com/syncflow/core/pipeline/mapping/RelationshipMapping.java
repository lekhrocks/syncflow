package com.syncflow.core.pipeline.mapping;

import java.util.List;

public record RelationshipMapping(
        String name,
        String sourceTable,
        String destinationTable,
        List<String> sourceColumns,
        List<String> destinationColumns,
        String type) {

    public static final String ONE_TO_ONE = "1:1";
    public static final String ONE_TO_MANY = "1:N";
    public static final String MANY_TO_MANY = "N:N";
}
