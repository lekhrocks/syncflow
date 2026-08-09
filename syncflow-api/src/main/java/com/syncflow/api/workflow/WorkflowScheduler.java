package com.syncflow.api.workflow;

import com.syncflow.core.workflow.TaskExecution;
import com.syncflow.core.workflow.TaskStatus;
import com.syncflow.core.workflow.TaskType;
import com.syncflow.core.workflow.WorkflowId;
import com.syncflow.core.workflow.WorkflowInstance;
import com.syncflow.core.workflow.WorkflowStatus;
import com.syncflow.core.workflow.WorkflowTask;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Orchestrates pipeline workflows as a DAG of tasks.
 * <p>
 * Tasks are executed in dependency order: a task becomes ready when every task
 * it {@code dependsOn} has a COMPLETED execution. The scheduler advances the
 * DAG by executing ready tasks (via the {@link TaskExecutor} map), recording a
 * {@link TaskExecution} for each attempt, and marking the workflow COMPLETED
 * when all tasks finish. The previous implementation never recorded task
 * completions, so the graph could never progress.
 */
@Component
public class WorkflowScheduler {

    private final TaskQueue taskQueue;
    private final WorkflowBuilder builder;
    private final MeterRegistry meterRegistry;
    private final Map<WorkflowId, WorkflowInstance> workflows = new ConcurrentHashMap<>();
    private final AtomicBoolean leader = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicReference<Instant> lastHeartbeat = new AtomicReference<>(Instant.now());

    /** Task-type → executor. Unmapped types record a COMPLETED no-op execution. */
    private final Map<TaskType, java.util.function.Function<String, Void>> taskExecutors =
            new ConcurrentHashMap<>();

    public WorkflowScheduler(TaskQueue taskQueue, WorkflowBuilder builder,
            MeterRegistry meterRegistry) {
        this.taskQueue = taskQueue;
        this.builder = builder;
        this.meterRegistry = meterRegistry;

        scheduler.scheduleAtFixedRate(this::tick, 0, 2, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::heartbeat, 0, 10, TimeUnit.SECONDS);
    }

    /** Register an executor for a task type (e.g. SNAPSHOT → snapshotExecutor::start). */
    public void registerExecutor(TaskType type, java.util.function.Function<String, Void> executor) {
        taskExecutors.put(type, executor);
    }

    public WorkflowInstance create(String pipelineId) {
        var tasks = builder.buildPipelineWorkflow(pipelineId);
        var instance = WorkflowInstance.create(pipelineId, tasks);
        workflows.put(instance.id(), instance);
        return instance;
    }

    public WorkflowInstance start(WorkflowId id) {
        var wf = workflows.get(id);
        if (wf == null)
            throw new NoSuchElementException("Workflow not found: " + id);
        var running = wf.withStatus(WorkflowStatus.RUNNING);
        workflows.put(id, running);

        var ready = findReadyTasks(running);
        ready.forEach(t -> taskQueue.enqueue(id.value(), t.taskId(), t.type().name(), running.pipelineId()));
        return running;
    }

    public WorkflowInstance get(WorkflowId id) {
        var wf = workflows.get(id);
        if (wf == null)
            throw new NoSuchElementException("Workflow not found: " + id);
        return wf;
    }

    public List<WorkflowInstance> list() {
        return List.copyOf(workflows.values());
    }

    public WorkflowInstance cancel(WorkflowId id) {
        var wf = workflows.get(id);
        if (wf == null)
            throw new NoSuchElementException();
        var cancelled = wf.withStatus(WorkflowStatus.CANCELLED);
        workflows.put(id, cancelled);
        return cancelled;
    }

    public int queueSize() {
        return taskQueue.size();
    }

    public void becomeLeader() {
        leader.set(true);
    }

    public boolean isLeader() {
        return leader.get();
    }

    /**
     * Resets leader flag and clears all tracked workflows. Used in test teardown.
     */
    public void reset() {
        leader.set(false);
        workflows.clear();
    }

    private void tick() {
        if (!leader.get())
            return;

        workflows.forEach((id, wf) -> {
            if (wf.status() != WorkflowStatus.RUNNING)
                return;

            var completed = wf.completedTaskIds();
            // A workflow is done when every task has a COMPLETED execution.
            if (wf.tasks().stream().allMatch(t -> completed.contains(t.taskId()))) {
                workflows.put(id, wf.completed(Instant.now()));
                return;
            }

            var ready = findReadyTasks(wf);
            for (var task : ready) {
                // Skip tasks already queued/in-flight (an execution exists, just not COMPLETED).
                if (isInFlight(wf, task.taskId()))
                    continue;
                execute(id, wf, task);
            }
        });
    }

    /** Execute a single ready task and record its outcome. */
    private void execute(WorkflowId id, WorkflowInstance wf, WorkflowTask task) {
        var executionId = UUID.randomUUID().toString();
        var started = Instant.now();
        var runningExec = new TaskExecution(executionId, task.taskId(), TaskStatus.RUNNING,
                "scheduler", null, task.retryCount() + 1, started, null);
        workflows.put(id, wf.withExecution(runningExec));

        try {
            var executor = taskExecutors.get(task.type());
            if (executor != null) {
                executor.apply(wf.pipelineId());
            }
            var done = new TaskExecution(executionId, task.taskId(), TaskStatus.COMPLETED,
                    "scheduler", null, task.retryCount() + 1, started, Instant.now());
            var current = workflows.get(id);
            workflows.put(id, current.withExecution(done));
            meterRegistry.counter("syncflow.workflow.tasks.completed",
                    "pipeline", wf.pipelineId()).increment();
        } catch (Exception e) {
            var failed = new TaskExecution(executionId, task.taskId(), TaskStatus.FAILED,
                    "scheduler", e.getMessage(), task.retryCount() + 1, started, Instant.now());
            var current = workflows.get(id);
            workflows.put(id, current.withExecution(failed));
            meterRegistry.counter("syncflow.workflow.tasks.failed",
                    "pipeline", wf.pipelineId()).increment();
        }
    }

    /** True if the task has a non-COMPLETED execution already (queued/running/failed). */
    private boolean isInFlight(WorkflowInstance wf, String taskId) {
        return wf.executions().stream().anyMatch(e -> e.taskId().equals(taskId)
                && e.status() != TaskStatus.COMPLETED);
    }

    private void heartbeat() {
        lastHeartbeat.set(Instant.now());
    }

    public boolean isLeaderAlive() {
        return Duration.between(lastHeartbeat.get(), Instant.now()).getSeconds() < 30;
    }

    private List<WorkflowTask> findReadyTasks(WorkflowInstance wf) {
        var completed = wf.completedTaskIds();
        return wf.tasks().stream()
                .filter(t -> !completed.contains(t.taskId()))
                .filter(t -> completed.containsAll(t.dependsOn()))
                .toList();
    }
}
