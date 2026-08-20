package com.syncflow.api.config;

import java.time.Duration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Runtime tunables. Bound from {@code syncflow.runtime.*} in application.yml.
 * All magic numbers (queue capacity, retry policy, poll timeout) live here;
 * orchestrators inject this bean instead of hard-coding constants.
 *
 * Enabled via {@code @EnableConfigurationProperties(RuntimeProperties.class)}
 * on the application class. Bean validation is enforced so misconfiguration
 * fails at startup rather than producing surprising runtime behaviour
 * (e.g. negative-capacity queues).
 */
@Setter
@Getter
@ConfigurationProperties("syncflow.runtime")
@Validated
public class RuntimeProperties {

    @NotNull
    private Sync sync = new Sync();
    @NotNull
    private Snapshot snapshot = new Snapshot();
    @NotNull
    private Retry retry = new Retry();

    @Setter
    @Getter
    public static class Sync {

        /** Capacity of the in-memory CDC event queue per pipeline. */
        @Min(value = 1, message = "queueCapacity must be at least 1")
        private int queueCapacity = 10000;
        /** Max events drained per batch by the worker. */
        @Min(value = 1, message = "batchSize must be at least 1")
        private int batchSize = 100;
        /** Poll timeout when the queue is empty. */
        @NotNull
        @Positive
        private Duration pollTimeout = Duration.ofMillis(500);

    }

    @Setter
    @Getter
    public static class Snapshot {

        /**
         * Checkpoint every N batches (cursor carry so resume continues at the next
         * row).
         */
        @Min(value = 1, message = "checkpointIntervalBatches must be at least 1")
        private int checkpointIntervalBatches = 5;

        /**
         * Publish live progress (persist + SSE) every N batches aggregated
         * across the parallel chunk workers. Serialized so concurrent workers
         * cannot clobber the JSON-payload progress write.
         */
        @Min(value = 1, message = "progressPublishIntervalBatches must be at least 1")
        private int progressPublishIntervalBatches = 10;

        /**
         * Number of parallel PK-range chunk workers per snapshot (F15). One per
         * chunk; tables with non-numeric PKs ignore this and run sequentially.
         */
        @Min(value = 1, message = "parallelism must be at least 1")
        private int parallelism = 4;

        /**
         * PK ranges split a table into at most this many chunks (throttle on the
         * batching emphasis: chunk count = batchSize, capped here). The upper
         * bound guards the per-chunk range allocation: each chunk is one
         * ChunkRange + one prepared statement, so a huge misconfiguration (e.g.
         * 1e9) would allocate gigabytes and stall snack-start.
         */
        @Min(value = 1, message = "maxChunks must be at least 1")
        @Max(value = 1024, message = "maxChunks must be at most 1024")
        private int maxChunks = 64;

    }

    @Setter
    @Getter
    public static class Retry {

        @Min(value = 1, message = "maxAttempts must be at least 1")
        private int maxAttempts = 3;
        @NotNull
        @Positive
        private Duration baseDelay = Duration.ofMillis(1000);

    }
}
