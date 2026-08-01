package com.syncflow.connector.writer;

import com.syncflow.core.model.ConnectorType;
import com.syncflow.core.spi.writer.DestinationWriter;
import com.syncflow.core.spi.writer.WriterRegistry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WriterRegistryImpl implements WriterRegistry {

    private final Map<ConnectorType, DestinationWriter> writers = new ConcurrentHashMap<>();

    public WriterRegistryImpl(List<DestinationWriter> writerBeans) {
        for (var w : writerBeans) {
            if (w instanceof PostgresWriter)
                writers.put(ConnectorType.POSTGRESQL, w);
            else if (w instanceof MySqlWriter)
                writers.put(ConnectorType.MYSQL, w);
        }
    }

    @Override
    public void register(ConnectorType type, DestinationWriter writer) {
        writers.put(type, writer);
    }

    @Override
    public Optional<DestinationWriter> get(ConnectorType type) {
        return Optional.ofNullable(writers.get(type));
    }
}
