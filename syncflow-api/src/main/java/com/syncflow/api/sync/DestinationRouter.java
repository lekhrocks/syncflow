package com.syncflow.api.sync;

import com.syncflow.api.connection.service.ConnectionService;
import com.syncflow.api.metadata.ConnectorTypeMapper;
import com.syncflow.core.cdc.CDCEvent;
import com.syncflow.core.model.ConnectionConfiguration;
import com.syncflow.core.spi.writer.WriterRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DestinationRouter {

    private final WriterRegistry writerRegistry;
    private final ConnectionService connectionService;

    public DestinationRouter(WriterRegistry writerRegistry,
            ConnectionService connectionService) {
        this.writerRegistry = writerRegistry;
        this.connectionService = connectionService;
    }

    public WriteResult write(String connectionId, CDCEvent event,
            List<String> destColumns) {
        var conn = connectionService.getWithDecryptedCredentials(connectionId);
        var ct = ConnectorTypeMapper.toCore(conn.getProperties().type());
        var writer = writerRegistry.get(ct)
                .orElseThrow(() -> new IllegalArgumentException("No writer for: " + ct));

        var config = toConfig(conn);
        writer.connect(config);

        try {
            var tableName = event.source().table();
            switch (event.operation()) {
                case INSERT -> {
                    if (event.payload().after() != null) {
                        writer.writeBatch(tableName, List.of(event.payload().after()), destColumns);
                    }
                }
                case UPDATE -> {
                    if (event.payload().after() != null) {
                        writer.writeBatch(tableName, List.of(event.payload().after()), destColumns);
                    }
                }
                case DELETE -> {
                    // ponytail: DELETE via writer not yet supported — insert with tombstone marker
                }
            }
            writer.flush();
            writer.commit();
            return new WriteResult(true, null);
        } catch (Exception e) {
            writer.rollback();
            return new WriteResult(false, e.getMessage());
        } finally {
            writer.close();
        }
    }

    private ConnectionConfiguration toConfig(com.syncflow.core.connection.Connection conn) {
        var p = conn.getProperties();
        var c = conn.getCredentials();
        return new ConnectionConfiguration(
                ConnectorTypeMapper.toCore(p.type()),
                p.host(), p.port(), p.database(),
                c.username(), c.password(), p.options());
    }

    public record WriteResult(boolean success, String error) {
    }
}
