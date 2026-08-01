package com.syncflow.api.ops.diagnostics;

import com.syncflow.api.connection.service.ConnectionService;
import com.syncflow.api.pipeline.PipelineDesignerService;
import com.syncflow.api.sync.DeadLetterQueue;
import com.syncflow.api.sync.SyncOrchestrator;
import com.syncflow.core.registry.ConnectorRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/diagnostics")
public class DiagnosticsController {

    private final ConnectorRegistry connectorRegistry;
    private final PipelineDesignerService pipelineService;
    private final ConnectionService connectionService;
    private final SyncOrchestrator syncOrchestrator;
    private final DeadLetterQueue dlq;

    public DiagnosticsController(ConnectorRegistry connectorRegistry,
            PipelineDesignerService pipelineService,
            ConnectionService connectionService,
            SyncOrchestrator syncOrchestrator,
            DeadLetterQueue dlq) {
        this.connectorRegistry = connectorRegistry;
        this.pipelineService = pipelineService;
        this.connectionService = connectionService;
        this.syncOrchestrator = syncOrchestrator;
        this.dlq = dlq;
    }

    @GetMapping("/system")
    public ResponseEntity<Map<String, Object>> system() {
        var runtime = Runtime.getRuntime();
        var memBean = ManagementFactory.getMemoryMXBean();
        var threadBean = ManagementFactory.getThreadMXBean();

        return ResponseEntity.ok(new LinkedHashMap<>() {

            {
                put("jvm", Map.of(
                        "availableProcessors", runtime.availableProcessors(),
                        "freeMemory", runtime.freeMemory(),
                        "totalMemory", runtime.totalMemory(),
                        "maxMemory", runtime.maxMemory(),
                        "heapMemoryUsage", memBean.getHeapMemoryUsage().getUsed(),
                        "nonHeapMemoryUsage", memBean.getNonHeapMemoryUsage().getUsed(),
                        "threadCount", threadBean.getThreadCount(),
                        "daemonThreadCount", threadBean.getDaemonThreadCount(),
                        "peakThreadCount", threadBean.getPeakThreadCount(),
                        "virtualThreadCount", Thread.getAllStackTraces().keySet().stream()
                                .filter(Thread::isVirtual).count()));
                put("os", Map.of(
                        "name", System.getProperty("os.name"),
                        "version", System.getProperty("os.version"),
                        "arch", System.getProperty("os.arch")));
                put("java", Map.of(
                        "version", System.getProperty("java.version"),
                        "vendor", System.getProperty("java.vendor"),
                        "vm", System.getProperty("java.vm.name")));
            }
        });
    }

    @GetMapping("/connectors")
    public ResponseEntity<Map<String, Object>> connectors() {
        var info = new LinkedHashMap<String, Object>();
        connectorRegistry.getAll().forEach(c -> {
            var h = c.health();
            info.put(c.type().name(), Map.of(
                    "status", h.status().name(),
                    "message", h.message(),
                    "latencyMs", h.latencyMs(),
                    "capabilities", c.capabilities(),
                    "connected", c.isConnected()));
        });
        return ResponseEntity.ok(info);
    }

    @GetMapping("/pipelines")
    public ResponseEntity<List<Object>> pipelines() {
        var list = new ArrayList<>();
        pipelineService.list().forEach(p -> list.add(Map.of(
                "id", p.id().value(),
                "name", p.name().value(),
                "status", p.status().name(),
                "version", p.audit().version(),
                "sourceTable", p.source().tableOrCollection(),
                "destTable", p.destination().tableOrCollection())));
        return ResponseEntity.ok(list);
    }

    @GetMapping("/executions")
    public ResponseEntity<Map<String, Object>> executions() {
        return ResponseEntity.ok(Map.of(
                "syncJobs", syncOrchestrator.list().size(),
                "dlqSize", dlq.count(),
                "activeCaptures", 0));
    }
}
