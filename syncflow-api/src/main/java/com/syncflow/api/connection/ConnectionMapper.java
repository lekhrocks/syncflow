package com.syncflow.api.connection;

import com.syncflow.api.metadata.ConnectorTypeMapper;
import com.syncflow.core.connection.Connection;
import com.syncflow.core.model.ConnectionConfiguration;

/**
 * Single source of truth for mapping a {@link Connection} domain object into a
 * {@link ConnectionConfiguration} suitable for connector execution.
 *
 * The mapping was previously duplicated in five places (SnapshotExecutor,
 * SyncOrchestrator, CaptureLifecycle, DestinationRouter,
 * PipelineDesignerService).
 * Centralising it here removes the divergence hazard: any change to
 * ConnectionConfiguration (e.g. adding a new field) propagates everywhere
 * without per-callsite edits.
 */
public final class ConnectionMapper {

    private ConnectionMapper() {
    }

    public static ConnectionConfiguration toConfig(Connection conn) {
        var p = conn.getProperties();
        var c = conn.getCredentials();
        return new ConnectionConfiguration(
                ConnectorTypeMapper.toCore(p.type()),
                p.host(), p.port(), p.database(),
                c.username(), c.password(), p.options());
    }
}
