package com.syncflow.tenant;

import java.util.UUID;

public record WorkspaceId(String value) {

    public WorkspaceId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException();
    }
    public static WorkspaceId generate() {
        return new WorkspaceId(UUID.randomUUID().toString());
    }
    public static WorkspaceId from(String v) {
        return new WorkspaceId(v);
    }
    @Override
    public String toString() {
        return value;
    }
}
