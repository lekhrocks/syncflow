package com.syncflow.api.kafka;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

/**
 * Creates Kafka topics for a pipeline before CDC capture starts.
 * Each source table gets its own topic: {@code {prefix}.{pipelineId}.{table}}.
 * Topic creation is idempotent — existing topics are silently skipped.
 */
@Component
@ConditionalOnProperty(name = "syncflow.kafka.enabled", havingValue = "true")
public class KafkaTopicProvisioner {

    private static final Logger log = LoggerFactory.getLogger(KafkaTopicProvisioner.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final KafkaProperties kafkaProperties;

    public KafkaTopicProvisioner(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    /**
     * Ensure topics exist for every table in {@code tables} under this pipeline.
     * Creates topics with the configured partition count and replication factor.
     */
    public void provisionTopics(String pipelineId, List<String> tables) {
        if (tables == null || tables.isEmpty()) {
            log.debug("No tables to provision topics for pipeline={}", pipelineId);
            return;
        }

        var adminProps = buildAdminProperties();
        try (var admin = AdminClient.create(adminProps)) {
            var newTopics = tables.stream()
                    .map(table -> buildTopic(pipelineId, table))
                    .toList();

            var result = admin.createTopics(newTopics);
            for (var entry : result.values().entrySet()) {
                try {
                    entry.getValue().get(TIMEOUT.toSeconds(), java.util.concurrent.TimeUnit.SECONDS);
                    log.info("Kafka topic created: {}", entry.getKey());
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof TopicExistsException) {
                        log.debug("Kafka topic already exists (skipping): {}", entry.getKey());
                    } else {
                        log.error("Failed to create Kafka topic {}: {}", entry.getKey(), e.getCause().getMessage());
                    }
                } catch (Exception e) {
                    log.error("Unexpected error creating topic {}: {}", entry.getKey(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to connect to Kafka for topic provisioning pipeline={}: {}",
                    pipelineId, e.getMessage());
        }
    }

    /** Delete all topics for a pipeline (called on pipeline deletion). */
    public void deleteTopics(String pipelineId, List<String> tables) {
        if (tables == null || tables.isEmpty())
            return;

        var topicNames = tables.stream()
                .map(t -> kafkaProperties.getTopic().topicFor(pipelineId, t))
                .toList();

        var adminProps = buildAdminProperties();
        try (var admin = AdminClient.create(adminProps)) {
            admin.deleteTopics(topicNames)
                    .all()
                    .get(TIMEOUT.toSeconds(), java.util.concurrent.TimeUnit.SECONDS);
            log.info("Deleted {} Kafka topics for pipeline={}", topicNames.size(), pipelineId);
        } catch (Exception e) {
            log.warn("Failed to delete Kafka topics for pipeline={}: {}", pipelineId, e.getMessage());
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private NewTopic buildTopic(String pipelineId, String table) {
        var cfg = kafkaProperties.getTopic();
        var topicName = cfg.topicFor(pipelineId, table);
        var topic = new NewTopic(topicName, cfg.getPartitions(), cfg.getReplicationFactor());
        topic.configs(Map.of(
                "retention.ms", String.valueOf(cfg.getRetentionMs()),
                "cleanup.policy", "delete",
                "compression.type", kafkaProperties.getProducer().getCompressionType()));
        return topic;
    }

    private Properties buildAdminProperties() {
        var props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) TIMEOUT.toMillis());
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, (int) TIMEOUT.toMillis());
        return props;
    }
}
