package com.syncflow.api.workflow;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class TaskQueue {

    private final LinkedBlockingQueue<TaskItem> queue = new LinkedBlockingQueue<>(10000);
    private final Map<String, TaskItem> store = new ConcurrentHashMap<>();

    public void enqueue(String workflowId, String taskId, String taskType, String pipelineId) {
        var item = new TaskItem(UUID.randomUUID().toString(), workflowId, taskId, taskType, pipelineId);
        store.put(item.id(), item);
        queue.offer(item);
    }

    public TaskItem dequeue() {
        return queue.poll();
    }

    public void complete(String itemId) {
        store.remove(itemId);
    }

    public int size() {
        return queue.size();
    }

    public List<TaskItem> pending() {
        return List.copyOf(store.values());
    }

    public record TaskItem(String id, String workflowId, String taskId, String taskType, String pipelineId) {
    }
}
