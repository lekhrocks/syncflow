package com.syncflow.agent.domain;

public record AgentId(String value) {

    public AgentId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException();
    }
    @Override
    public String toString() {
        return value;
    }
}
