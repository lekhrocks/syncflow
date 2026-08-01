package com.syncflow.api.cdc;

import com.syncflow.api.connection.service.ConnectionService;
import com.syncflow.api.pipeline.PipelineDesignerService;
import com.syncflow.core.cdc.CaptureStatus;
import com.syncflow.core.connection.Connection;
import com.syncflow.core.connection.ConnectionId;
import com.syncflow.core.connection.ConnectionMetadata;
import com.syncflow.core.connection.ConnectionProperties;
import com.syncflow.core.connection.ConnectionStatus;
import com.syncflow.core.connection.ConnectionType;
import com.syncflow.core.connection.Credentials;
import com.syncflow.core.model.ConnectorType;
import com.syncflow.core.pipeline.DestinationReference;
import com.syncflow.core.pipeline.PipelineDesign;
import com.syncflow.core.pipeline.PipelineName;
import com.syncflow.core.pipeline.PipelineSettings;
import com.syncflow.core.pipeline.SourceReference;
import com.syncflow.core.registry.ConnectorRegistry;
import com.syncflow.core.spi.CdcCapableConnector;
import com.syncflow.core.spi.ConnectorContext;
import com.syncflow.core.spi.ValidationResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CaptureLifecycle")
class CaptureLifecycleUnitTest {

    @Mock
    private PipelineDesignerService pipelineService;
    @Mock
    private ConnectionService connectionService;
    @Mock
    private ConnectorRegistry connectorRegistry;
    @Mock
    private OffsetStore offsetStore;
    @Mock
    private CdcCapableConnector cdcConnector;

    private CaptureLifecycle lifecycle;

    @BeforeEach
    void setUp() {
        lifecycle = new CaptureLifecycle(pipelineService, connectionService,
                connectorRegistry, offsetStore, new SimpleMeterRegistry());
    }

    // ── Test data helpers ────────────────────────────────────────────────────

    private PipelineDesign pipeline(String pipelineId) {
        return PipelineDesign.create(
                new PipelineName("test-pipeline"),
                new SourceReference("conn-src", "public", "users"),
                new DestinationReference("conn-dst", "public", "users_copy", "UPSERT"),
                List.of(), PipelineSettings.defaults());
    }

    private Connection pgConnection() {
        var props = new ConnectionProperties(ConnectionType.POSTGRESQL,
                "localhost", 5432, "syncflow", Map.of());
        var creds = new Credentials("user", "pass");
        return Connection.restore(ConnectionId.generate(), "pg-conn", props, creds,
                ConnectionStatus.VALID, ConnectionMetadata.unknown(),
                Instant.now(), Instant.now());
    }

    private void mockStartup(String pipelineId) {
        var design = pipeline(pipelineId);
        var conn = pgConnection();
        when(pipelineService.get(pipelineId)).thenReturn(design);
        when(connectionService.getWithDecryptedCredentials(design.source().connectionId()))
                .thenReturn(conn);
        when(connectorRegistry.get(ConnectorType.POSTGRESQL))
                .thenReturn(Optional.of(cdcConnector));
        when(cdcConnector.validate(any(ConnectorContext.class)))
                .thenReturn(ValidationResult.ok());
        org.mockito.Mockito.lenient()
                .when(cdcConnector.captureStatus()).thenReturn(CaptureStatus.INACTIVE);
        when(offsetStore.get(pipelineId)).thenReturn(Map.of());
    }

    // ── start() ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("start()")
    class Start {

        @Test
        void returnsRunningStatusOnSuccess() {
            mockStartup("p-1");
            var status = lifecycle.start("p-1", null);
            assertEquals(CaptureStatus.RUNNING, status);
        }

        @Test
        void startsCdcOnConnector() {
            mockStartup("p-1");
            lifecycle.start("p-1", null);
            verify(cdcConnector).startCDC(any(ConnectorContext.class), any(Consumer.class));
        }

        @Test
        void runsPreFlightValidation() {
            mockStartup("p-1");
            lifecycle.start("p-1", null);
            verify(cdcConnector).validate(any(ConnectorContext.class));
        }

        @Test
        void throwsWhenValidationFails() {
            var design = pipeline("p-fail");
            var conn = pgConnection();
            when(pipelineService.get("p-fail")).thenReturn(design);
            when(connectionService.getWithDecryptedCredentials(any())).thenReturn(conn);
            when(connectorRegistry.get(ConnectorType.POSTGRESQL))
                    .thenReturn(Optional.of(cdcConnector));
            when(cdcConnector.validate(any()))
                    .thenReturn(ValidationResult.failed(List.of("wal_level not logical")));

            assertThrows(IllegalStateException.class, () -> lifecycle.start("p-fail", null));
            verify(cdcConnector, never()).startCDC(any(), any());
        }

        @Test
        void throwsWhenNoCdcConnectorFound() {
            var design = pipeline("p-no-cdc");
            var conn = pgConnection();
            when(pipelineService.get("p-no-cdc")).thenReturn(design);
            when(connectionService.getWithDecryptedCredentials(any())).thenReturn(conn);
            when(connectorRegistry.get(ConnectorType.POSTGRESQL)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> lifecycle.start("p-no-cdc", null));
        }

        @Test
        void skipsRestartWhenAlreadyRunning() {
            mockStartup("p-1");
            when(cdcConnector.captureStatus()).thenReturn(CaptureStatus.RUNNING);

            // First start
            lifecycle.start("p-1", null);
            // Second start — should return immediately without re-starting
            when(cdcConnector.captureStatus()).thenReturn(CaptureStatus.RUNNING);
            lifecycle.start("p-1", null);

            // startCDC should only be called once
            verify(cdcConnector).startCDC(any(), any());
        }

        @Test
        void loadsAndLogsSavedOffset() {
            mockStartup("p-1");
            when(offsetStore.get("p-1")).thenReturn(Map.of("lsn", "0/ABCDEF"));
            lifecycle.start("p-1", null);
            verify(offsetStore).get("p-1");
        }
    }

    // ── stop() ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("stop()")
    class Stop {

        @Test
        void stopsCdcAndSavesOffset() {
            mockStartup("p-1");
            lifecycle.start("p-1", null);

            when(cdcConnector.currentOffset())
                    .thenReturn(Map.of("lsn", "0/AABBCC", "connectorType", "POSTGRESQL"));
            lifecycle.stop("p-1");

            verify(cdcConnector).stopCDC();
            verify(offsetStore).save(eq("p-1"), any());
        }

        @Test
        void noopWhenNotStarted() {
            lifecycle.stop("not-started");
            verify(cdcConnector, never()).stopCDC();
        }

        @Test
        void doesNotSaveOffsetWhenEmpty() {
            mockStartup("p-1");
            lifecycle.start("p-1", null);
            when(cdcConnector.currentOffset()).thenReturn(Map.of());
            lifecycle.stop("p-1");
            verify(offsetStore, never()).save(any(), any());
        }
    }

    // ── pause() / resume() ────────────────────────────────────────────────────

    @Nested
    @DisplayName("pause() and resume()")
    class PauseResume {

        @Test
        void pauseDelegatestoConnector() {
            mockStartup("p-1");
            lifecycle.start("p-1", null);
            lifecycle.pause("p-1");
            verify(cdcConnector).pauseCDC();
        }

        @Test
        void resumeDelegatestoConnector() {
            mockStartup("p-1");
            lifecycle.start("p-1", null);
            lifecycle.resume("p-1");
            verify(cdcConnector).resumeCDC();
        }

        @Test
        void pauseNoopWhenNotStarted() {
            lifecycle.pause("not-started");
            verify(cdcConnector, never()).pauseCDC();
        }
    }

    // ── status() ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("status()")
    class Status {

        @Test
        void returnsInactiveWhenNotStarted() {
            assertEquals(CaptureStatus.INACTIVE, lifecycle.status("unknown"));
        }

        @Test
        void returnsConnectorStatusWhenStarted() {
            mockStartup("p-1");
            lifecycle.start("p-1", null);
            when(cdcConnector.captureStatus()).thenReturn(CaptureStatus.RUNNING);
            assertEquals(CaptureStatus.RUNNING, lifecycle.status("p-1"));
        }
    }

    // ── eventCount() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("eventCount()")
    class EventCount {

        @Test
        void returnsZeroWhenNotStarted() {
            assertEquals(0, lifecycle.eventCount("unknown"));
        }

        @Test
        void usesInterfaceCountNotCast() {
            // Verifies fix #6 — no ClassCastException from blind cast to
            // InMemoryEventPublisher
            mockStartup("p-1");
            lifecycle.start("p-1", null);
            // Should not throw ClassCastException
            var count = lifecycle.eventCount("p-1");
            assertEquals(0, count); // BoundedQueueEventPublisher starts at 0
        }
    }

    // ── shutdownAll() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("shutdownAll()")
    class ShutdownAll {

        @Test
        void stopsAllActiveCaptures() {
            mockStartup("p-1");
            lifecycle.start("p-1", null);
            when(cdcConnector.currentOffset()).thenReturn(Map.of());

            lifecycle.shutdownAll();

            verify(cdcConnector).stopCDC();
            assertEquals(CaptureStatus.INACTIVE, lifecycle.status("p-1"));
        }
    }
}
