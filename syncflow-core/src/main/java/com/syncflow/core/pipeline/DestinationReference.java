package com.syncflow.core.pipeline;

public record DestinationReference(
        String connectionId,
        String schema,
        String tableOrCollection,
        String writeMode) {

    public static final String INSERT = "INSERT";
    public static final String UPSERT = "UPSERT";
    public static final String MERGE = "MERGE";

    public DestinationReference {
        if (writeMode == null)
            writeMode = UPSERT;
    }
}
