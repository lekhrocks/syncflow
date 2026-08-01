package com.syncflow.agent.domain;

public record AssignedWork(
        String workId,
        String workType,
        String pipelineId,
        String status) {
}
