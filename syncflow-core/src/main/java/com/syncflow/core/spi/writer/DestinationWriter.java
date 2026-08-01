package com.syncflow.core.spi.writer;

import com.syncflow.core.model.ConnectionConfiguration;
import java.util.List;
import java.util.Map;

public interface DestinationWriter extends AutoCloseable {

    void connect(ConnectionConfiguration config);

    void writeBatch(String table, List<Map<String, Object>> rows, List<String> columns);

    void flush();

    void commit();

    void rollback();

    @Override
    void close();

    boolean isConnected();
}
