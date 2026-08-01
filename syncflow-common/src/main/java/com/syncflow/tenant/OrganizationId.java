package com.syncflow.tenant;

import java.util.UUID;

public record OrganizationId(String value) {

    public OrganizationId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException();
    }
    public static OrganizationId generate() {
        return new OrganizationId(UUID.randomUUID().toString());
    }
    public static OrganizationId from(String v) {
        return new OrganizationId(v);
    }
    @Override
    public String toString() {
        return value;
    }
}
