package com.syncflow.api.workflow;

import com.syncflow.core.workflow.TaskType;
import com.syncflow.core.workflow.WorkflowTask;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class WorkflowBuilder {

    public List<WorkflowTask> buildPipelineWorkflow(String pipelineId) {
        var tasks = new ArrayList<WorkflowTask>();

        tasks.add(task("validate", "Validate Pipeline", TaskType.VALIDATION, List.of(),
                Map.of("pipelineId", pipelineId)));

        tasks.add(task("metadata", "Discover Metadata", TaskType.METADATA_DISCOVERY,
                List.of("validate"), Map.of("pipelineId", pipelineId)));

        tasks.add(task("snapshot", "Snapshot Data", TaskType.SNAPSHOT,
                List.of("metadata"), Map.of("pipelineId", pipelineId)));

        tasks.add(task("cdc", "Start CDC Capture", TaskType.CDC_CAPTURE,
                List.of("metadata"), Map.of("pipelineId", pipelineId)));

        tasks.add(task("sync", "Synchronize", TaskType.SYNCHRONIZATION,
                List.of("snapshot"), Map.of("pipelineId", pipelineId)));

        tasks.add(task("monitor", "Monitor Execution", TaskType.MONITORING,
                List.of("sync", "cdc"), Map.of("pipelineId", pipelineId)));

        return tasks;
    }

    private WorkflowTask task(String id, String name, TaskType type,
            List<String> dependsOn, Map<String, String> input) {
        return new WorkflowTask(id, name, type, 0, 3, Duration.ofMinutes(30),
                dependsOn, false, input);
    }
}
