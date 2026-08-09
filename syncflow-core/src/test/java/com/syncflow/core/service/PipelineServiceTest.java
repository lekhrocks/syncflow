package com.syncflow.core.service;

import com.syncflow.common.exception.SyncFlowException;
import com.syncflow.core.model.ConnectionConfiguration;
import com.syncflow.core.model.ConnectorType;
import com.syncflow.core.model.PipelineStatus;
import com.syncflow.core.registry.SpringConnectorRegistry;
import com.syncflow.core.repository.InMemoryPipelineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PipelineServiceTest {

    private PipelineService service;

    @BeforeEach
    void setUp() {
        var repo = new InMemoryPipelineRepository();
        var registry = new SpringConnectorRegistry(List.of());
        service = new PipelineService(repo, registry);
    }

    private ConnectionConfiguration config() {
        return new ConnectionConfiguration(ConnectorType.POSTGRESQL, "localhost", 5432,
                "testdb", "user", "pass", Map.of());
    }

    @Test
    void createPipeline_returnsPipeline() {
        var p = service.create("test", config(), config(), null);
        assertNotNull(p.getId());
        assertEquals("test", p.getName());
        assertEquals(PipelineStatus.CREATED, p.getStatus());
    }

    @Test
    void getPipeline_notFound_throws() {
        assertThrows(SyncFlowException.class, () -> service.get("does-not-exist"));
    }

    @Test
    void deletePipeline_thenStatusDeleted() {
        var p = service.create("test", config(), config(), null);
        service.delete(p.getId());
        var deleted = service.get(p.getId());
        assertEquals(PipelineStatus.DELETED, deleted.getStatus());
    }

    @Test
    void startPipeline_validatesConnector() {
        var p = service.create("test", config(), config(), null);
        assertThrows(SyncFlowException.class, () -> service.start(p.getId()));
    }
}
