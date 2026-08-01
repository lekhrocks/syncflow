package com.syncflow.connector.kafka;

import com.syncflow.core.model.ConnectorType;
import com.syncflow.core.spi.Connector;
import com.syncflow.core.spi.ConnectorCapabilities;
import com.syncflow.core.spi.ConnectorContext;
import com.syncflow.core.spi.ConnectorHealth;
import com.syncflow.core.spi.ValidationResult;

import java.util.List;
import java.util.Map;

// ponytail: kept for backward compat; Kafka connector will be implemented in a future phase
public class KafkaConnector implements Connector {

    @Override
    public ConnectorType type() {
        return ConnectorType.KAFKA;
    }

    @Override
    public ConnectorCapabilities capabilities() {
        return new ConnectorCapabilities(true, false, false, false, true);
    }

    @Override
    public void connect(ConnectorContext ctx) {
        /* stub */ }

    @Override
    public void disconnect() {
        /* stub */ }

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public ValidationResult validate(ConnectorContext ctx) {
        return ValidationResult.ok();
    }

    @Override
    public List<String> discoverSchemas(ConnectorContext ctx) {
        return List.of();
    }

    @Override
    public List<String> discoverTables(ConnectorContext ctx, String schema) {
        return List.of();
    }

    @Override
    public ConnectorHealth health() {
        return ConnectorHealth.unknown();
    }

    @Override
    public Map<String, Object> metadata() {
        return Map.of("version", "3.9+", "vendor", "Apache Kafka");
    }
}
