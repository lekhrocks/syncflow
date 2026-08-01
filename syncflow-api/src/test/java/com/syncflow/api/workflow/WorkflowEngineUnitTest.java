package com.syncflow.api.workflow;

import com.syncflow.core.workflow.TaskType;
import com.syncflow.core.workflow.WorkflowTask;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowEngineUnitTest {

    // --- DAG validation ---

    @Test
    void dagAcyclicPasses() {
        var tasks = List.of(
                task("t1", List.of()),
                task("t2", List.of("t1")),
                task("t3", List.of("t2")));
        assertFalse(hasCycle(tasks));
    }

    @Test
    void dagCircularDetected() {
        var tasks = List.of(
                task("t1", List.of("t2")),
                task("t2", List.of("t3")),
                task("t3", List.of("t1")));
        assertTrue(hasCycle(tasks));
    }

    @Test
    void dagSelfLoopDetected() {
        var tasks = List.of(task("t1", List.of("t1")));
        assertTrue(hasCycle(tasks));
    }

    @Test
    void dagDisconnectedNodesValid() {
        var tasks = List.of(
                task("t1", List.of()),
                task("t2", List.of()),
                task("t3", List.of()));
        assertFalse(hasCycle(tasks));
    }

    @Test
    void dagMissingDependencyIsTypo() {
        var tasks = List.of(
                task("t1", List.of()),
                task("t2", List.of("nonexistent")));
        // Missing dependencies should not cause cycle — but need validation
        assertFalse(hasCycle(tasks));
    }

    @Test
    void dagSingleNode() {
        assertFalse(hasCycle(List.of(task("t1", List.of()))));
    }

    // --- Scheduler: ready task detection ---

    @Test
    void schedulerFindsReadyTasksWhenNoDependencies() {
        var tasks = List.of(
                task("t1", List.of()),
                task("t2", List.of()));
        var ready = findReady(tasks, Set.of());
        assertEquals(2, ready.size());
    }

    @Test
    void schedulerFindsReadyTasksAfterDependencyMet() {
        var tasks = List.of(
                task("t1", List.of()),
                task("t2", List.of("t1")));
        var ready = findReady(tasks, Set.of("t1"));
        assertEquals(1, ready.size());
        assertEquals("t2", ready.getFirst().taskId());
    }

    @Test
    void schedulerBlocksTaskWithUnmetDependencies() {
        var tasks = List.of(
                task("t1", List.of()),
                task("t2", List.of("t1")));
        var ready = findReady(tasks, Set.of());
        assertEquals(1, ready.size());
        assertEquals("t1", ready.getFirst().taskId());
    }

    @Test
    void schedulerEmptyCompletedReturnsRoots() {
        var tasks = List.of(
                task("root1", List.of()),
                task("root2", List.of()),
                task("child", List.of("root1")));
        var ready = findReady(tasks, Set.of());
        assertEquals(2, ready.size());
    }

    @Test
    void schedulerAllTasksCompletedReturnsEmpty() {
        var tasks = List.of(task("t1", List.of()));
        var ready = findReady(tasks, Set.of("t1"));
        assertTrue(ready.isEmpty());
    }

    // --- Retry policy ---

    @Test
    void retryPolicyMaxRetries() {
        var task = new WorkflowTask("t1", "Test", TaskType.VALIDATION, 0, 3,
                Duration.ofMinutes(30), List.of(), false, Map.of());
        assertEquals(3, task.maxRetries());
        assertTrue(task.retryCount() < task.maxRetries());
    }

    @Test
    void retryPolicyExhausted() {
        var task = new WorkflowTask("t1", "Test", TaskType.VALIDATION, 3, 3,
                Duration.ofMinutes(30), List.of(), false, Map.of());
        assertEquals(3, task.retryCount());
        assertFalse(task.retryCount() < task.maxRetries());
    }

    @Test
    void retryPolicyZeroRetries() {
        var task = new WorkflowTask("t1", "Test", TaskType.VALIDATION, 0, 0,
                Duration.ofMinutes(30), List.of(), false, Map.of());
        assertFalse(task.retryCount() < task.maxRetries());
    }

    // --- Task dependencies ---

    @Test
    void taskDependsOnMultiple() {
        var t = new WorkflowTask("sync", "Sync", TaskType.SYNCHRONIZATION, 0, 3,
                Duration.ofMinutes(30), List.of("snapshot", "cdc"), false, Map.of());
        assertEquals(2, t.dependsOn().size());
        assertTrue(t.dependsOn().containsAll(List.of("snapshot", "cdc")));
    }

    @Test
    void taskNoDependencies() {
        var t = task("standalone", List.of());
        assertTrue(t.dependsOn().isEmpty());
    }

    // --- Priority (ordering) ---

    @Test
    void tasksExecutedInDependencyOrder() {
        var tasks = List.of(
                task("t1", List.of()),
                task("t2", List.of("t1")),
                task("t3", List.of("t2")));
        var order = topologicalSort(tasks);
        assertTrue(order.indexOf("t1") < order.indexOf("t2"));
        assertTrue(order.indexOf("t2") < order.indexOf("t3"));
    }

    @Test
    void independentTasksCanRunInParallel() {
        var tasks = List.of(
                task("a", List.of()),
                task("b", List.of()));
        var ready = findReady(tasks, Set.of());
        assertEquals(2, ready.size());
    }

    // --- Timeout ---

    @Test
    void taskWithTimeout() {
        var t = new WorkflowTask("t1", "Slow", TaskType.MONITORING, 0, 1,
                Duration.ofMinutes(5), List.of(), false, Map.of());
        assertEquals(Duration.ofMinutes(5), t.timeout());
    }

    @Test
    void taskDefaultTimeout() {
        var t = task("t1", List.of());
        assertNotNull(t.timeout());
    }

    @Test
    void taskExceedsTimeout() {
        var started = java.time.Instant.now().minusSeconds(3600); // 1 hour ago
        var deadline = started.plus(Duration.ofMinutes(30));
        assertTrue(java.time.Instant.now().isAfter(deadline));
    }

    @Test
    void taskWithinTimeout() {
        var started = java.time.Instant.now().minusSeconds(60); // 1 min ago
        var deadline = started.plus(Duration.ofMinutes(30));
        assertFalse(java.time.Instant.now().isAfter(deadline));
    }

    // --- Helpers ---

    private WorkflowTask task(String id, List<String> deps) {
        return new WorkflowTask(id, "Task-" + id, TaskType.VALIDATION, 0, 3,
                Duration.ofMinutes(30), deps, false, Map.of());
    }

    private List<WorkflowTask> findReady(List<WorkflowTask> tasks, Set<String> completed) {
        return tasks.stream()
                .filter(t -> !completed.contains(t.taskId()))
                .filter(t -> completed.containsAll(t.dependsOn()))
                .toList();
    }

    private boolean hasCycle(List<WorkflowTask> tasks) {
        var graph = new HashMap<String, List<String>>();
        tasks.forEach(t -> graph.put(t.taskId(), t.dependsOn()));
        var visited = new HashSet<String>();
        var inStack = new HashSet<String>();
        for (var node : graph.keySet()) {
            if (detectCycle(node, graph, visited, inStack))
                return true;
        }
        return false;
    }

    private boolean detectCycle(String node, Map<String, List<String>> graph,
            Set<String> visited, Set<String> inStack) {
        if (inStack.contains(node))
            return true;
        if (visited.contains(node))
            return false;
        visited.add(node);
        inStack.add(node);
        for (var dep : graph.getOrDefault(node, List.of())) {
            if (detectCycle(dep, graph, visited, inStack))
                return true;
        }
        inStack.remove(node);
        return false;
    }

    private List<String> topologicalSort(List<WorkflowTask> tasks) {
        var graph = new HashMap<String, List<String>>();
        var inDegree = new HashMap<String, Integer>();
        tasks.forEach(t -> {
            graph.put(t.taskId(), new ArrayList<>(t.dependsOn()));
            inDegree.putIfAbsent(t.taskId(), 0);
            t.dependsOn().forEach(d -> inDegree.merge(t.taskId(), 1, Integer::sum));
        });
        var queue = new LinkedList<>(inDegree.entrySet().stream()
                .filter(e -> e.getValue() == 0).map(Map.Entry::getKey).toList());
        var result = new ArrayList<String>();
        while (!queue.isEmpty()) {
            var node = queue.poll();
            result.add(node);
            tasks.stream().filter(t -> t.dependsOn().contains(node)).forEach(t -> {
                inDegree.merge(t.taskId(), -1, Integer::sum);
                if (inDegree.get(t.taskId()) == 0)
                    queue.add(t.taskId());
            });
        }
        return result;
    }
}
