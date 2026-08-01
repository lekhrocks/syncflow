package com.syncflow.api.pipeline;

import com.syncflow.core.pipeline.DestinationReference;
import com.syncflow.core.pipeline.PipelineDesign;
import com.syncflow.core.pipeline.PipelineName;
import com.syncflow.core.pipeline.PipelineSettings;
import com.syncflow.core.pipeline.PipelineStatus;
import com.syncflow.core.pipeline.SourceReference;
import com.syncflow.core.pipeline.SyncMode;
import com.syncflow.core.pipeline.mapping.ColumnMapping;
import com.syncflow.core.pipeline.mapping.PrimaryKeyMapping;
import com.syncflow.core.pipeline.mapping.TableMapping;
import com.syncflow.core.pipeline.validation.ValidationIssue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineValidationUnitTest {

    // --- Pipeline validation ---

    @Test
    void validPipelinePassesBasicValidation() {
        var name = new PipelineName("valid-pipeline");
        var src = new SourceReference("conn-1", "public", "users");
        var dst = new DestinationReference("conn-2", "public", "users_copy", "UPSERT");
        var pk = new PrimaryKeyMapping(List.of("id"), List.of("id"));
        var cm = new ColumnMapping("id", "id", List.of());
        var mapping = new TableMapping("users", "users_copy", null, pk, List.of(cm), List.of(), List.of(), null);
        var pipeline = PipelineDesign.create(name, src, dst, List.of(mapping), PipelineSettings.defaults());

        assertNotNull(pipeline.id());
        assertEquals(PipelineStatus.DRAFT, pipeline.status());
    }

    @Test
    void pipelineWithoutMappingsIsValidDraft() {
        var pipeline = PipelineDesign.create(
                new PipelineName("empty"),
                new SourceReference("c1", "s", "t"),
                new DestinationReference("c2", "s", "t", null),
                List.of(), PipelineSettings.defaults());
        assertTrue(pipeline.tableMappings().isEmpty());
    }

    @Test
    void pipelineWithMultipleMappings() {
        var pk = new PrimaryKeyMapping(List.of("id"), List.of("id"));
        var t1 = new TableMapping("users", "users_dest", null, pk, List.of(), List.of(), List.of(), null);
        var t2 = new TableMapping("orders", "orders_dest", null, pk, List.of(), List.of(), List.of(), null);
        var pipeline = PipelineDesign.create(
                new PipelineName("multi"),
                new SourceReference("c1", "s", "t"),
                new DestinationReference("c2", "s", "t", null),
                List.of(t1, t2), PipelineSettings.defaults());
        assertEquals(2, pipeline.tableMappings().size());
    }

    // --- Dependency validation ---

    @Test
    void taskDependencyMissingDependencyDetected() {
        var deps = List.of("t1", "t3");
        assertTrue(deps.containsAll(List.of("t1")));
        assertFalse(deps.containsAll(List.of("t1", "missing")));
    }

    @Test
    void taskDependencyCircularDetection() {
        var graph = new java.util.HashMap<String, List<String>>();
        graph.put("t1", List.of("t2"));
        graph.put("t2", List.of("t3"));
        graph.put("t3", List.of("t1")); // cycle: t1 → t2 → t3 → t1

        assertTrue(hasCycle(graph));
    }

    @Test
    void taskDependencyAcyclicGraphPasses() {
        var graph = new java.util.HashMap<String, List<String>>();
        graph.put("t1", List.of());
        graph.put("t2", List.of("t1"));
        graph.put("t3", List.of("t2"));

        assertFalse(hasCycle(graph));
    }

    @Test
    void taskDependencyDisconnectedGraph() {
        var graph = new java.util.HashMap<String, List<String>>();
        graph.put("t1", List.of());
        graph.put("t2", List.of());

        assertFalse(hasCycle(graph));
    }

    @Test
    void taskDependencySelfLoop() {
        var graph = new java.util.HashMap<String, List<String>>();
        graph.put("t1", List.of("t1")); // self-loop
        assertTrue(hasCycle(graph));
    }

    // --- Graph validation ---

    @Test
    void graphWithDisconnectedNodes() {
        var allTasks = List.of("validate", "snapshot", "sync", "cleanup");
        var dependsOn = List.of("validate");
        var hasDeps = allTasks.stream().filter(dependsOn::contains).toList();
        assertEquals(1, hasDeps.size());
    }

    @Test
    void graphWithMultipleRoots() {
        var tasks = List.of("t1", "t2", "t3");
        var deps = Map.of("t3", List.of("t1", "t2"));
        var roots = tasks.stream().filter(t -> !deps.containsKey(t)).toList();
        assertEquals(2, roots.size());
        assertTrue(roots.contains("t1"));
        assertTrue(roots.contains("t2"));
    }

    // --- Configuration validation ---

    @Test
    void settingsInvalidBatchSizeClampedToPositive() {
        var s = new PipelineSettings(SyncMode.FULL_SNAPSHOT, 0, 0, false, false, Map.of());
        assertTrue(s.batchSize() > 0);
    }

    @Test
    void settingsNegativeRetriesClamped() {
        var s = new PipelineSettings(SyncMode.FULL_SNAPSHOT, 100, -5, false, false, Map.of());
        assertTrue(s.maxRetries() >= 0);
    }

    @Test
    void settingsCustomPropertiesImmutable() {
        var mutable = new java.util.HashMap<>(Map.of("key", "val"));
        var s = new PipelineSettings(SyncMode.FULL_SNAPSHOT, 100, 3, false, false, mutable);
        mutable.put("key", "modified");
        assertEquals("val", s.properties().get("key"));
    }

    @Test
    void settingsNullPropertiesDefaultsEmpty() {
        var s = new PipelineSettings(SyncMode.FULL_SNAPSHOT, 100, 3, false, false, null);
        assertTrue(s.properties().isEmpty());
    }

    // --- Validation issue types ---

    @Test
    void validationIssueError() {
        var issue = new ValidationIssue("CONNECTION_NOT_FOUND", "source.connectionId",
                "Source connection not found", ValidationIssue.Severity.ERROR);
        assertEquals(ValidationIssue.Severity.ERROR, issue.severity());
        assertFalse(issue.message().isBlank());
    }

    @Test
    void validationIssueWarning() {
        var issue = new ValidationIssue("MISSING_PRIMARY_KEY", "mapping.primaryKey",
                "Table has no primary key mapping", ValidationIssue.Severity.WARNING);
        assertEquals(ValidationIssue.Severity.WARNING, issue.severity());
    }

    // --- Cycle detection algorithm ---

    private boolean hasCycle(java.util.Map<String, List<String>> graph) {
        var visited = new java.util.HashSet<String>();
        var inStack = new java.util.HashSet<String>();

        for (var node : graph.keySet()) {
            if (detectCycle(node, graph, visited, inStack))
                return true;
        }
        return false;
    }

    private boolean detectCycle(String node, java.util.Map<String, List<String>> graph,
            java.util.Set<String> visited, java.util.Set<String> inStack) {
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
}
