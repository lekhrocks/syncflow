package com.syncflow.api.workflow;

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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class WorkflowScheduler {

    private final TaskQueue taskQueue;
    private final WorkflowBuilder builder;
    private final MeterRegistry meterRegistry;
    private final Map<WorkflowId, WorkflowInstance> workflows = new ConcurrentHashMap<>();
    private final AtomicBoolean leader = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicReference<Instant> lastHeartbeat = new AtomicReference<>(Instant.now());

    public WorkflowScheduler(TaskQueue taskQueue, WorkflowBuilder builder,
            MeterRegistry meterRegistry) {
        this.taskQueue = taskQueue;
        this.builder = builder;
        this.meterRegistry = meterRegistry;

        scheduler.scheduleAtFixedRate(this::tick, 0, 2, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::heartbeat, 0, 10, TimeUnit.SECONDS);
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

    /** Resets leader flag and clears all tracked workflows. Used in test teardown. */
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

            var completedTasks = completedTaskIds(wf);
            var ready = wf.tasks().stream()
                    .filter(t -> !completedTasks.contains(t.taskId()))
                    .filter(t -> completedTasks.containsAll(t.dependsOn()))
                    .toList();

            ready.forEach(t -> taskQueue.enqueue(
                    id.value(), t.taskId(), t.type().name(), wf.pipelineId()));

            meterRegistry.gauge("syncflow.workflow.queue.size", taskQueue.size());
        });
    }

    private void heartbeat() {
        lastHeartbeat.set(Instant.now());
    }

    public boolean isLeaderAlive() {
        return Duration.between(lastHeartbeat.get(), Instant.now()).getSeconds() < 30;
    }

    private Set<String> completedTaskIds(WorkflowInstance wf) {
        return Set.of();
    }

    private List<WorkflowTask> findReadyTasks(WorkflowInstance wf) {
        var completed = completedTaskIds(wf);
        return wf.tasks().stream()
                .filter(t -> completed.containsAll(t.dependsOn()))
                .toList();
    }
}
