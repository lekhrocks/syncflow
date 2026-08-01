package com.syncflow.core.snapshot;

import java.util.UUID;

public record SnapshotId(String value) {

    public SnapshotId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("SnapshotId must not be blank");
    }
    public static SnapshotId generate() {
        return new SnapshotId(UUID.randomUUID().toString());
    }
    public static SnapshotId from(String v) {
        return new SnapshotId(v);
    }
    @Override
    public String toString() {
        return value;
    }
}
