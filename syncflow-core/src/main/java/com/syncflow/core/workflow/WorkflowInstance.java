package com.syncflow.core.workflow;

import java.time.Instant;
import java.util.List;

public record WorkflowInstance(WorkflowId id, String pipelineId, WorkflowStatus status, List<WorkflowTask> tasks,
        List<TaskExecution> executions, Instant createdAt, Instant completedAt) {

    public WorkflowInstance(WorkflowId id, String pipelineId, WorkflowStatus status,
            List<WorkflowTask> tasks, List<TaskExecution> executions,
            Instant createdAt, Instant completedAt) {
        this.id = id;
        this.pipelineId = pipelineId;
        this.status = status;
        this.tasks = List.copyOf(tasks);
        this.executions = List.copyOf(executions);
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    public WorkflowInstance withStatus(WorkflowStatus s) {
        return new WorkflowInstance(id, pipelineId, s, tasks, executions, createdAt, completedAt);
    }

    public static WorkflowInstance create(String pipelineId, List<WorkflowTask> tasks) {
        return new WorkflowInstance(WorkflowId.generate(), pipelineId, WorkflowStatus.PENDING,
                tasks, List.of(), Instant.now(), null);
    }
}
