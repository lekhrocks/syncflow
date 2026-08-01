package com.syncflow.tenant;

import java.util.UUID;

public record TenantId(String value) {

    public TenantId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TenantId must not be blank");
        }
    }

    public static TenantId generate() {
        return new TenantId(UUID.randomUUID().toString());
    }

    public static TenantId from(String value) {
        return new TenantId(value);
    }

    public static final TenantId DEFAULT = new TenantId("00000000-0000-0000-0000-000000000000");

    @Override
    public String toString() {
        return value;
    }
}
