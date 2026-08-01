package com.syncflow.core.workflow;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowDomainTest {

    @Test
    void createWorkflowInstance() {
        var tasks = List.of(
                new WorkflowTask("t1", "Validation", TaskType.VALIDATION, 0, 3,
                        Duration.ofMinutes(30), List.of(), false, java.util.Map.of()));
        var wf = WorkflowInstance.create("pipeline-1", tasks);
        assertNotNull(wf.id());
        assertEquals("pipeline-1", wf.pipelineId());
        assertEquals(WorkflowStatus.PENDING, wf.status());
        assertEquals(1, wf.tasks().size());
    }

    @Test
    void statusTransitions() {
        var tasks = List.of(new WorkflowTask("t1", "Test", TaskType.VALIDATION, 0, 3,
                Duration.ofMinutes(30), List.of(), false, java.util.Map.of()));
        var wf = WorkflowInstance.create("p-1", tasks);

        var running = wf.withStatus(WorkflowStatus.RUNNING);
        assertEquals(WorkflowStatus.RUNNING, running.status());

        var completed = running.withStatus(WorkflowStatus.COMPLETED);
        assertEquals(WorkflowStatus.COMPLETED, completed.status());

        var cancelled = wf.withStatus(WorkflowStatus.CANCELLED);
        assertEquals(WorkflowStatus.CANCELLED, cancelled.status());
    }

    @Test
    void taskExecutionRoundTrip() {
        var exec = new TaskExecution("e-1", "t-1", TaskStatus.RUNNING, "worker-1",
                null, 1, java.time.Instant.now(), null);
        assertEquals("t-1", exec.taskId());
        assertEquals(TaskStatus.RUNNING, exec.status());
        assertEquals("worker-1", exec.workerId());
    }

    @Test
    void workflowTaskWithDependencies() {
        var t2 = new WorkflowTask("t2", "Snapshot", TaskType.SNAPSHOT, 0, 3,
                Duration.ofMinutes(30), List.of("t1"), false, java.util.Map.of());
        assertEquals(List.of("t1"), t2.dependsOn());
    }

    @Test
    void workflowTaskCompensation() {
        var t = new WorkflowTask("comp", "Rollback", TaskType.COMPENSATION, 0, 3,
                Duration.ofMinutes(30), List.of(), true, java.util.Map.of());
        assertTrue(t.isCompensation());
    }

    @Test
    void taskStatusValues() {
        assertNotNull(TaskStatus.valueOf("PENDING"));
        assertNotNull(TaskStatus.valueOf("COMPLETED"));
        assertNotNull(TaskStatus.valueOf("FAILED"));
        assertNotNull(TaskStatus.valueOf("TIMED_OUT"));
    }

    @Test
    void workflowIdGenerator() {
        var id1 = WorkflowId.generate();
        var id2 = WorkflowId.generate();
        assertNotNull(id1);
        assertNotEquals(id1, id2);
    }

    @Test
    void workflowIdRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> WorkflowId.from(""));
        assertThrows(IllegalArgumentException.class, () -> WorkflowId.from(null));
    }
}
