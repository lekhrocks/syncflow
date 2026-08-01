package com.syncflow.plugin.spi;

import java.util.List;
import java.util.Map;

public interface DestinationWriterProvider extends AutoCloseable {

    void connect(PluginContext context);

    void write(String table, List<Map<String, Object>> rows);

    void flush();

    void commit();

    void rollback();

    @Override
    void close();
}
