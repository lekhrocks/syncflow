package com.syncflow.core.snapshot;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.UUID;

public record SnapshotId(String value) {

    public SnapshotId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("SnapshotId must not be blank");
    }

    @JsonCreator
    public static SnapshotId from(String v) {
        return new SnapshotId(v);
    }

    public static SnapshotId generate() {
        return new SnapshotId(UUID.randomUUID().toString());
    }

    @JsonValue
    @Override
    public String toString() {
        return value;
    }
}
