package com.syncflow.api.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.syncflow.api.runtimestate.RuntimeStateJson;
import com.syncflow.api.workflow.entity.WorkflowInstanceEntity;
import com.syncflow.api.workflow.repository.WorkflowInstanceRepository;
import com.syncflow.core.workflow.TaskExecution;
import com.syncflow.core.workflow.WorkflowId;
import com.syncflow.core.workflow.WorkflowInstance;
import com.syncflow.core.workflow.WorkflowStatus;
import com.syncflow.core.workflow.WorkflowTask;
import com.syncflow.tenant.TenantSupport;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class WorkflowScheduler {

    private final TaskQueue taskQueue;
    private final WorkflowBuilder builder;
    private final WorkflowInstanceRepository repository;
    private final RuntimeStateJson json;
    private final MeterRegistry meterRegistry;

    // Leader election + heartbeat stay in-memory (transient); workflow instances
    // are durable in workflow_instances.
    private final AtomicBoolean leader = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicReference<Instant> lastHeartbeat = new AtomicReference<>(Instant.now());

    public WorkflowScheduler(TaskQueue taskQueue, WorkflowBuilder builder,
            WorkflowInstanceRepository repository, RuntimeStateJson json,
            MeterRegistry meterRegistry) {
        this.taskQueue = taskQueue;
        this.builder = builder;
        this.repository = repository;
        this.json = json;
        this.meterRegistry = meterRegistry;

        scheduler.scheduleAtFixedRate(this::tick, 0, 2, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::heartbeat, 0, 10, TimeUnit.SECONDS);
    }

    @Transactional
    public WorkflowInstance create(String pipelineId) {
        var tasks = builder.buildPipelineWorkflow(pipelineId);
        var instance = WorkflowInstance.create(pipelineId, tasks);
        persist(instance);
        return instance;
    }

    @Transactional
    public WorkflowInstance start(WorkflowId id) {
        var wf = get(id);
        var running = wf.withStatus(WorkflowStatus.RUNNING);
        persist(running);

        var ready = findReadyTasks(running);
        ready.forEach(t -> taskQueue.enqueue(id.value(), t.taskId(), t.type().name(), running.pipelineId()));
        return running;
    }

    @Transactional(readOnly = true)
    public WorkflowInstance get(WorkflowId id) {
        return findOwned(id)
                .orElseThrow(() -> new NoSuchElementException("Workflow not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<WorkflowInstance> list() {
        return repository.findByTenantIdOrderByCreatedAtDesc(TenantSupport.tenantId()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Transactional
    public WorkflowInstance cancel(WorkflowId id) {
        var wf = get(id);
        var cancelled = wf.withStatus(WorkflowStatus.CANCELLED);
        persist(cancelled);
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
    }

    private void tick() {
        if (!leader.get())
            return;

        list().forEach(wf -> {
            if (wf.status() != WorkflowStatus.RUNNING)
                return;

            var completedTasks = completedTaskIds(wf);
            var ready = wf.tasks().stream()
                    .filter(t -> !completedTasks.contains(t.taskId()))
                    .filter(t -> completedTasks.containsAll(t.dependsOn()))
                    .toList();

            ready.forEach(t -> taskQueue.enqueue(
                    wf.id().value(), t.taskId(), t.type().name(), wf.pipelineId()));

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

    private Optional<WorkflowInstance> findOwned(WorkflowId id) {
        return repository.findById(id.value())
                .filter(e -> TenantSupport.tenantId().equals(e.getTenantId()))
                .map(this::toDomain);
    }

    @Transactional
    private void persist(WorkflowInstance wf) {
        var entity = repository.findById(wf.id().value()).orElseGet(WorkflowInstanceEntity::new);
        entity.setId(wf.id().value());
        entity.setTenantId(TenantSupport.tenantId());
        entity.setPipelineId(wf.pipelineId());
        entity.setStatus(wf.status().name());
        entity.setTasks(json.toJson(wf.tasks()));
        entity.setExecutions(json.toJson(wf.executions()));
        entity.setCreatedAt(wf.createdAt());
        entity.setCompletedAt(wf.completedAt());
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
    }

    private WorkflowInstance toDomain(WorkflowInstanceEntity e) {
        return WorkflowInstance.restore(WorkflowId.from(e.getId()), e.getPipelineId(),
                WorkflowStatus.valueOf(e.getStatus()),
                json.fromJson(e.getTasks(), new TypeReference<List<WorkflowTask>>() {
                }),
                json.fromJson(e.getExecutions(), new TypeReference<List<TaskExecution>>() {
                }),
                e.getCreatedAt(), e.getCompletedAt());
    }
}
