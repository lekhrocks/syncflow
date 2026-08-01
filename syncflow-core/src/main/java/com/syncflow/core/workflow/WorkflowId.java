package com.syncflow.core.workflow;

import java.util.UUID;

public record WorkflowId(String value) {

    public WorkflowId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException();
    }
    public static WorkflowId generate() {
        return new WorkflowId(UUID.randomUUID().toString());
    }
    public static WorkflowId from(String v) {
        return new WorkflowId(v);
    }
    @Override
    public String toString() {
        return value;
    }
}
