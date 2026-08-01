package com.syncflow.tenant;

import java.util.UUID;

public record ProjectId(String value) {

    public ProjectId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException();
    }
    public static ProjectId generate() {
        return new ProjectId(UUID.randomUUID().toString());
    }
    public static ProjectId from(String v) {
        return new ProjectId(v);
    }
    @Override
    public String toString() {
        return value;
    }
}
