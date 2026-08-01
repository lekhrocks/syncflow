package com.syncflow.core.spi;

import com.syncflow.core.model.ConnectorType;
import java.util.List;
import java.util.Map;

public interface Connector {

    ConnectorType type();

    ConnectorCapabilities capabilities();

    void connect(ConnectorContext context);

    void disconnect();

    boolean isConnected();

    ValidationResult validate(ConnectorContext context);

    List<String> discoverSchemas(ConnectorContext context);

    List<String> discoverTables(ConnectorContext context, String schema);

    ConnectorHealth health();

    Map<String, Object> metadata();
}
