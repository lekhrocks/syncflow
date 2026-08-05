package com.syncflow.api.kafka;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Strongly-typed binding for {@code syncflow.kafka.*} configuration.
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "syncflow.kafka")
public class KafkaProperties {

    private boolean enabled = false;
    private String bootstrapServers = "localhost:9092";
    private Topic topic = new Topic();
    private Producer producer = new Producer();
    private Consumer consumer = new Consumer();

    @Setter
    @Getter
    public static class Topic {

        private String prefix = "syncflow";
        private int partitions = 3;
        private short replicationFactor = 1;
        private long retentionMs = 604_800_000L; // 7 days

        /** Derive topic name: {@code {prefix}.{pipelineId}.{table}} */
        public String topicFor(String pipelineId, String table) {
            return prefix + "." + sanitize(pipelineId) + "." + sanitize(table);
        }

        private String sanitize(String value) {
            return value == null ? "default" : value.replaceAll("[^a-zA-Z0-9._-]", "_");
        }
    }

    @Setter
    @Getter
    public static class Producer {

        private String acks = "all";
        private int retries = 3;
        private int batchSize = 65536;
        private int lingerMs = 5;
        private String compressionType = "snappy";

    }

    @Setter
    @Getter
    public static class Consumer {

        private String groupId = "syncflow-consumer";
        private String autoOffsetReset = "earliest";
        private boolean enableAutoCommit = false;
        private int maxPollRecords = 500;

    }
}
