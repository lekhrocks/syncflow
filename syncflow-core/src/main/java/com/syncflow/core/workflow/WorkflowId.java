package com.syncflow.core.workflow;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.UUID;

public record WorkflowId(String value) {

    public WorkflowId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException();
    }

    @JsonCreator
    public static WorkflowId from(String v) {
        return new WorkflowId(v);
    }

    public static WorkflowId generate() {
        return new WorkflowId(UUID.randomUUID().toString());
    }

    @JsonValue
    @Override
    public String toString() {
        return value;
    }
}
