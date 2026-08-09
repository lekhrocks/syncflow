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

    /** Append a task execution record (started/completed/failed). */
    public WorkflowInstance withExecution(TaskExecution execution) {
        var execs = new java.util.ArrayList<>(executions);
        execs.add(execution);
        return new WorkflowInstance(id, pipelineId, status, tasks, List.copyOf(execs), createdAt, completedAt);
    }

    /** Mark the workflow completed at the given time. */
    public WorkflowInstance completed(Instant at) {
        return new WorkflowInstance(id, pipelineId, WorkflowStatus.COMPLETED, tasks, executions, createdAt, at);
    }

    /** Tasks that have a COMPLETED execution (drives DAG progression). */
    public java.util.Set<String> completedTaskIds() {
        return executions.stream()
                .filter(e -> e.status() == TaskStatus.COMPLETED)
                .map(TaskExecution::taskId)
                .collect(java.util.stream.Collectors.toSet());
    }

    public static WorkflowInstance create(String pipelineId, List<WorkflowTask> tasks) {
        return new WorkflowInstance(WorkflowId.generate(), pipelineId, WorkflowStatus.PENDING,
                tasks, List.of(), Instant.now(), null);
    }
}
