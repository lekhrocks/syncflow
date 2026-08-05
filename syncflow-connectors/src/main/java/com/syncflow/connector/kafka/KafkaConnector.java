package com.syncflow.connector.kafka;

import com.syncflow.core.model.ConnectorType;
import com.syncflow.core.spi.Connector;
import com.syncflow.core.spi.ConnectorCapabilities;
import com.syncflow.core.spi.ConnectorContext;
import com.syncflow.core.spi.ConnectorHealth;
import com.syncflow.core.spi.ValidationResult;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Kafka connector — validates broker connectivity and cluster metadata.
 * Does not produce or consume messages (handled by KafkaEventPublisher /
 * KafkaCdcConsumer in syncflow-api).
 */
public class KafkaConnector implements Connector {

    private static final Logger log = LoggerFactory.getLogger(KafkaConnector.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private volatile boolean connected = false;

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
        var result = validate(ctx);
        if (result.valid()) {
            connected = true;
            log.info("Kafka connector connected to {}", bootstrapServers(ctx));
        } else {
            connected = false;
            log.warn("Kafka connector failed to connect: {}", result.errors());
        }
    }

    @Override
    public void disconnect() {
        connected = false;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    /**
     * Real pre-flight validation:
     * 1. TCP connectivity to the broker(s) via a short-lived AdminClient
     * 2. Confirm at least one broker is reachable
     */
    @Override
    public ValidationResult validate(ConnectorContext ctx) {
        var errors = new ArrayList<String>();
        var servers = bootstrapServers(ctx);

        var adminProps = new Properties();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, servers);
        adminProps.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) TIMEOUT.toMillis());
        adminProps.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, (int) TIMEOUT.toMillis());

        try (var admin = AdminClient.create(adminProps)) {
            var nodes = admin.describeCluster()
                    .nodes()
                    .get(TIMEOUT.toSeconds(), java.util.concurrent.TimeUnit.SECONDS);

            if (nodes == null || nodes.isEmpty()) {
                errors.add("Kafka cluster at " + servers + " returned no brokers.");
            } else {
                log.debug("Kafka validation passed: {} broker(s) reachable at {}",
                        nodes.size(), servers);
            }
        } catch (Exception e) {
            errors.add("Cannot reach Kafka broker(s) at " + servers + ": " + e.getMessage());
        }

        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.failed(errors);
    }

    @Override
    public List<String> discoverSchemas(ConnectorContext ctx) {
        // In Kafka, "schemas" are topics — list all topics visible to this client
        var adminProps = new Properties();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers(ctx));
        adminProps.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) TIMEOUT.toMillis());

        try (var admin = AdminClient.create(adminProps)) {
            return new ArrayList<>(admin.listTopics().names()
                    .get(TIMEOUT.toSeconds(), java.util.concurrent.TimeUnit.SECONDS));
        } catch (Exception e) {
            log.warn("Failed to list Kafka topics: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<String> discoverTables(ConnectorContext ctx, String schema) {
        return discoverSchemas(ctx); // topics serve as both schema and table
    }

    @Override
    public ConnectorHealth health() {
        return connected ? ConnectorHealth.up(0) : ConnectorHealth.unknown();
    }

    @Override
    public Map<String, Object> metadata() {
        return Map.of("connectorType", "KAFKA", "version", "3.9+", "vendor", "Apache Kafka");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String bootstrapServers(ConnectorContext ctx) {
        // bootstrap-servers stored in connection properties: host:port
        var cfg = ctx.config();
        var servers = cfg.properties().get("bootstrap.servers");
        if (servers != null && !servers.isBlank())
            return servers;
        // fallback: construct from host:port
        return cfg.host() + ":" + cfg.port();
    }
}
