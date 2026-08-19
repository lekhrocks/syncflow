package com.syncflow.api.cdc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncflow.persistence.cdc.repository.ActiveCaptureRepository;
import com.syncflow.api.connection.service.ConnectionService;
import com.syncflow.api.kafka.KafkaCdcConsumer;
import com.syncflow.api.lock.DistributedLockService;
import com.syncflow.api.kafka.KafkaProperties;
import com.syncflow.api.kafka.KafkaTopicProvisioner;
import com.syncflow.api.pipeline.PipelineDesignerService;
import com.syncflow.core.cdc.CaptureStatus;
import com.syncflow.tenant.TenantContext;
import com.syncflow.tenant.TenantContextHolder;
import com.syncflow.tenant.TenantId;
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
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
    @Mock
    private KafkaProperties kafkaProperties;
    @Mock
    private KafkaTopicProvisioner topicProvisioner;
    @Mock
    private KafkaCdcConsumer kafkaCdcConsumer;
    @Mock
    private ActiveCaptureRepository activeCaptureRepository;
    @Mock
    private DistributedLockService lockService;

    private CaptureLifecycle lifecycle;

    @BeforeEach
    void setUp() {
        lifecycle = new CaptureLifecycle(pipelineService, connectionService,
                connectorRegistry, offsetStore, new SimpleMeterRegistry(),
                new ObjectMapper(), activeCaptureRepository, lockService,
                Optional.of(kafkaProperties),
                Optional.of(topicProvisioner), Optional.of(kafkaCdcConsumer));
        // F13: withLock wraps doStart/doStop. Execute the action inline so the
        // behavior-under-test (lock-protected critical section) is exercised.
        // lenient: sub-tests that don't touch a lock would otherwise trip
        // Mockito's strict-unnecessary-stubbing check.
        Mockito.lenient()
                .when(lockService.withLock(any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    Supplier<?> action = inv.getArgument(3);
                    return action.get();
                });
        // Provide a default tenant context so test calls that read
        // TenantContextHolder.get() do not NPE in unit tests.
        TenantContextHolder.set(
                new TenantContext(new TenantId("00000000-0000-0000-0000-000000000000"),
                        null, null, null, "test-user", Set.of(),
                        Instant.now()));
    }

    /** Mirrors CaptureLifecycle.tenantKey(tenantId, pipelineId). */
    private static String tenantKeyOf(TenantContext ctx, String pipelineId) {
        return (ctx != null ? ctx.tenantId().value() : "00000000-0000-0000-0000-000000000000")
                + ":" + pipelineId;
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
        Mockito.lenient()
                .when(cdcConnector.captureStatus()).thenReturn(CaptureStatus.INACTIVE);
        when(offsetStore.get(tenantKeyOf(TenantContextHolder.get(), pipelineId))).thenReturn(Map.of());
        // Kafka disabled → bounded-queue path, no Kafka components started
        when(kafkaProperties.isEnabled()).thenReturn(false);
    }

    // ── start() ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("start()")
    class Start {

        @Test
        void returnsRunningStatusOnSuccess() {
            mockStartup("p-1");
            var status = lifecycle.start("p-1", null, TenantContextHolder.get());
            assertEquals(CaptureStatus.RUNNING, status);
        }

        @Test
        void startsCdcOnConnector() {
            mockStartup("p-1");
            lifecycle.start("p-1", null, TenantContextHolder.get());
            verify(cdcConnector).startCDC(any(ConnectorContext.class), any(Consumer.class));
        }

        @Test
        void runsPreFlightValidation() {
            mockStartup("p-1");
            lifecycle.start("p-1", null, TenantContextHolder.get());
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

            assertThrows(IllegalStateException.class,
                    () -> lifecycle.start("p-fail", null, TenantContextHolder.get()));
            verify(cdcConnector, never()).startCDC(any(), any());
        }

        @Test
        void throwsWhenNoCdcConnectorFound() {
            var design = pipeline("p-no-cdc");
            var conn = pgConnection();
            when(pipelineService.get("p-no-cdc")).thenReturn(design);
            when(connectionService.getWithDecryptedCredentials(any())).thenReturn(conn);
            when(connectorRegistry.get(ConnectorType.POSTGRESQL)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> lifecycle.start("p-no-cdc", null, TenantContextHolder.get()));
        }

        @Test
        void skipsRestartWhenAlreadyRunning() {
            mockStartup("p-1");
            when(cdcConnector.captureStatus()).thenReturn(CaptureStatus.RUNNING);

            // First start
            lifecycle.start("p-1", null, TenantContextHolder.get());
            // Second start — should return immediately without re-starting
            when(cdcConnector.captureStatus()).thenReturn(CaptureStatus.RUNNING);
            lifecycle.start("p-1", null, TenantContextHolder.get());

            // startCDC should only be called once
            verify(cdcConnector).startCDC(any(), any());
        }

        @Test
        void loadsAndLogsSavedOffset() {
            mockStartup("p-1");
            var key = tenantKeyOf(TenantContextHolder.get(), "p-1");
            when(offsetStore.get(key)).thenReturn(Map.of("lsn", "0/ABCDEF"));
            lifecycle.start("p-1", null, TenantContextHolder.get());
            verify(offsetStore).get(key);
        }
    }

    // ── stop() ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("stop()")
    class Stop {

        @Test
        void stopsCdcAndSavesOffset() {
            mockStartup("p-1");
            lifecycle.start("p-1", null, TenantContextHolder.get());

            when(cdcConnector.currentOffset())
                    .thenReturn(Map.of("lsn", "0/AABBCC", "connectorType", "POSTGRESQL"));
            lifecycle.stop("p-1", TenantContextHolder.get());

            verify(cdcConnector).stopCDC();
            verify(offsetStore).save(eq(tenantKeyOf(TenantContextHolder.get(), "p-1")), any());
        }

        @Test
        void noopWhenNotStarted() {
            lifecycle.stop("not-started", TenantContextHolder.get());
            verify(cdcConnector, never()).stopCDC();
        }

        @Test
        void doesNotSaveOffsetWhenEmpty() {
            mockStartup("p-1");
            lifecycle.start("p-1", null, TenantContextHolder.get());
            when(cdcConnector.currentOffset()).thenReturn(Map.of());
            lifecycle.stop("p-1", TenantContextHolder.get());
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
            lifecycle.start("p-1", null, TenantContextHolder.get());
            lifecycle.pause("p-1", TenantContextHolder.get());
            verify(cdcConnector).pauseCDC();
        }

        @Test
        void resumeDelegatestoConnector() {
            mockStartup("p-1");
            lifecycle.start("p-1", null, TenantContextHolder.get());
            lifecycle.resume("p-1", TenantContextHolder.get());
            verify(cdcConnector).resumeCDC();
        }

        @Test
        void pauseNoopWhenNotStarted() {
            lifecycle.pause("not-started", TenantContextHolder.get());
            verify(cdcConnector, never()).pauseCDC();
        }
    }

    // ── status() ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("status()")
    class Status {

        @Test
        void returnsInactiveWhenNotStarted() {
            assertEquals(CaptureStatus.INACTIVE,
                    lifecycle.status("unknown", TenantContextHolder.get()));
        }

        @Test
        void returnsConnectorStatusWhenStarted() {
            mockStartup("p-1");
            lifecycle.start("p-1", null, TenantContextHolder.get());
            when(cdcConnector.captureStatus()).thenReturn(CaptureStatus.RUNNING);
            assertEquals(CaptureStatus.RUNNING, lifecycle.status("p-1", TenantContextHolder.get()));
        }
    }

    // ── eventCount() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("eventCount()")
    class EventCount {

        @Test
        void returnsZeroWhenNotStarted() {
            assertEquals(0, lifecycle.eventCount("unknown", TenantContextHolder.get()));
        }

        @Test
        void usesInterfaceCountNotCast() {
            // Verifies fix #6 — no ClassCastException from blind cast to
            // InMemoryEventPublisher
            mockStartup("p-1");
            lifecycle.start("p-1", null, TenantContextHolder.get());
            // Should not throw ClassCastException
            var count = lifecycle.eventCount("p-1", TenantContextHolder.get());
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
            lifecycle.start("p-1", null, TenantContextHolder.get());
            when(cdcConnector.currentOffset()).thenReturn(Map.of());

            lifecycle.shutdownAllGlobal(CaptureLifecycle.GlobalShutdown.CONFIRMED);

            verify(cdcConnector).stopCDC();
            assertEquals(CaptureStatus.INACTIVE,
                    lifecycle.status("p-1", TenantContextHolder.get()));
        }
    }
}
