package com.syncflow.api.snapshot;

import com.syncflow.core.snapshot.SnapshotCheckpoint;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CheckpointStore {

    private final Map<String, SnapshotCheckpoint> store = new ConcurrentHashMap<>();

    public void save(SnapshotCheckpoint checkpoint) {
        store.put(key(checkpoint.pipelineId(), checkpoint.sourceTable()), checkpoint);
    }

    public SnapshotCheckpoint get(String pipelineId, String sourceTable) {
        return store.get(key(pipelineId, sourceTable));
    }

    public void delete(String pipelineId, String sourceTable) {
        store.remove(key(pipelineId, sourceTable));
    }

    public void deleteAll(String pipelineId) {
        store.keySet().removeIf(k -> k.startsWith(pipelineId + ":"));
    }

    private static String key(String pid, String table) {
        return pid + ":" + table;
    }
}
