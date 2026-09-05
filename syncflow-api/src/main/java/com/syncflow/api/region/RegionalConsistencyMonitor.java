package com.syncflow.api.region;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Monitors data consistency across regional replicas.
 *
 * <p>
 * Runs periodic checks to compare event counts, offset counts, and other
 * metrics between primary
 * and replicas. Detects divergence and emits metrics for alerting.
 *
 * <p>
 * Example metrics:
 * - regional.consistency.lag (milliseconds of replication lag)
 * - regional.events.divergence (event count difference between primary and
 * replica)
 * - regional.consistency.status (0=OK, 1=DIVERGED, 2=ERROR)
 */
@Component
public class RegionalConsistencyMonitor {

    private static final Logger logger = LoggerFactory.getLogger(RegionalConsistencyMonitor.class);

    private final RegionalProperties regionalProperties;
    private final RegionalDataSourceFactory dataSourceFactory;
    private final MeterRegistry meterRegistry;
    private final ScheduledExecutorService consistencyCheckExecutor;

    public RegionalConsistencyMonitor(
            RegionalProperties regionalProperties,
            RegionalDataSourceFactory dataSourceFactory,
            MeterRegistry meterRegistry) {
        this.regionalProperties = regionalProperties;
        this.dataSourceFactory = dataSourceFactory;
        this.meterRegistry = meterRegistry;
        this.consistencyCheckExecutor = new ScheduledThreadPoolExecutor(1, r -> {
            var t = new Thread(r, "regional-consistency-monitor");
            t.setDaemon(true);
            return t;
        });

        if (regionalProperties.isReplicationEnabled() && !regionalProperties.getRegions().isEmpty()) {
            startConsistencyChecking();
        }
    }

    /** Start periodic consistency checks (every 60 seconds). */
    private void startConsistencyChecking() {
        consistencyCheckExecutor.scheduleAtFixedRate(
                this::checkRegionalConsistency, 60, 60, TimeUnit.SECONDS);
        logger.info("Started regional consistency monitor");
    }

    /** Compare data between primary and replicas. */
    private void checkRegionalConsistency() {
        var primaryRegion = dataSourceFactory.getCurrentPrimaryRegion();

        try {
            var primaryCounts = getEventCounts(primaryRegion);

            for (var region : regionalProperties.getRegions().keySet()) {
                if (region.equals(primaryRegion))
                    continue;

                try {
                    var replicaCounts = getEventCounts(region);
                    var divergence = analyzeConsistency(primaryCounts, replicaCounts, primaryRegion, region);
                    recordConsistencyMetrics(divergence, region);
                } catch (Exception e) {
                    logger.warn("Failed to check consistency for region {}", region, e);
                    recordConsistencyError(region);
                }
            }
        } catch (Exception e) {
            logger.error("Regional consistency check failed", e);
        }
    }

    /**
     * Get event and offset counts from a region.
     *
     * @param region
     *            region to check
     * @return map of metrics (processed_events, pending_events, offset_records)
     */
    private Map<String, Long> getEventCounts(String region) {
        var counts = new HashMap<String, Long>();

        try (var conn = dataSourceFactory.getReadDataSource(region).getConnection();
                var stmt = conn.createStatement()) {

            // Count processed events
            var rs = stmt.executeQuery("SELECT COUNT(*) FROM processed_events");
            if (rs.next()) {
                counts.put("processed_events", rs.getLong(1));
            }

            // Count pending events in DLQ
            rs = stmt.executeQuery("SELECT COUNT(*) FROM dlq_events WHERE status = 'PENDING'");
            if (rs.next()) {
                counts.put("pending_events", rs.getLong(1));
            }

            // Count CDC offsets
            rs = stmt.executeQuery("SELECT COUNT(*) FROM debezium_offsets");
            if (rs.next()) {
                counts.put("offset_records", rs.getLong(1));
            }

            // Get current WAL position (for lag calculation)
            rs = stmt.executeQuery("SELECT pg_current_wal_lsn()::TEXT");
            if (rs.next()) {
                counts.put("wal_lsn_hash", hashLsn(rs.getString(1)));
            }

            return counts;
        } catch (Exception e) {
            logger.debug("Failed to get event counts for region {}", region, e);
            throw new RuntimeException("Cannot get event counts for " + region, e);
        }
    }

    /**
     * Analyze divergence between primary and replica.
     *
     * @return ConsistencyReport with differences
     */
    private ConsistencyReport analyzeConsistency(
            Map<String, Long> primaryCounts,
            Map<String, Long> replicaCounts,
            String primaryRegion,
            String replicaRegion) {

        var report = new ConsistencyReport();
        report.primaryRegion = primaryRegion;
        report.replicaRegion = replicaRegion;

        // Check processed events
        var primaryEvents = primaryCounts.getOrDefault("processed_events", 0L);
        var replicaEvents = replicaCounts.getOrDefault("processed_events", 0L);
        report.eventDivergence = primaryEvents - replicaEvents;

        // Check pending events
        var primaryPending = primaryCounts.getOrDefault("pending_events", 0L);
        var replicaPending = replicaCounts.getOrDefault("pending_events", 0L);
        report.pendingDivergence = Math.abs(primaryPending - replicaPending);

        // Check offset records
        var primaryOffsets = primaryCounts.getOrDefault("offset_records", 0L);
        var replicaOffsets = replicaCounts.getOrDefault("offset_records", 0L);
        report.offsetDivergence = primaryOffsets - replicaOffsets;

        // Detect divergence (more than 1000 events or 100 pending)
        report.isDiverged = Math.abs(report.eventDivergence) > 1000 || Math.abs(report.pendingDivergence) > 100;

        if (report.isDiverged) {
            logger.warn(
                    "Regional divergence detected: {} vs {} - events lag: {}, pending lag: {}",
                    primaryRegion,
                    replicaRegion,
                    report.eventDivergence,
                    report.pendingDivergence);
        }

        return report;
    }

    /** Record consistency metrics to Micrometer. */
    private void recordConsistencyMetrics(ConsistencyReport report, String region) {
        meterRegistry.gauge(
                "regional.consistency.event_lag",
                Math.abs(report.eventDivergence));

        meterRegistry.gauge(
                "regional.consistency.pending_lag",
                Math.abs(report.pendingDivergence));

        meterRegistry.gauge(
                "regional.consistency.offset_lag",
                Math.abs(report.offsetDivergence));

        meterRegistry.gauge(
                "regional.consistency.status",
                report.isDiverged ? 1.0 : 0.0);
    }

    /** Record consistency error for a region. */
    private void recordConsistencyError(String region) {
        meterRegistry
                .counter(
                        "regional.consistency.errors",
                        "region", region)
                .increment();
    }

    /** Simple hash of LSN for comparison. */
    private long hashLsn(String lsn) {
        return lsn.hashCode();
    }

    /** Shutdown monitoring. */
    public void shutdown() {
        consistencyCheckExecutor.shutdown();
        try {
            if (!consistencyCheckExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                consistencyCheckExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            consistencyCheckExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Regional consistency monitor shut down");
    }

    /**
     * Public API for manual consistency check (useful for health endpoints).
     *
     * @return report of current consistency status
     */
    public String getConsistencyStatus() {
        try {
            var primaryRegion = dataSourceFactory.getCurrentPrimaryRegion();
            var primaryCounts = getEventCounts(primaryRegion);
            var allRegions = new StringBuilder();

            for (var region : regionalProperties.getRegions().keySet()) {
                if (region.equals(primaryRegion)) {
                    allRegions.append(
                            String.format(
                                    "[%s PRIMARY] events=%d ",
                                    region, primaryCounts.getOrDefault("processed_events", 0L)));
                } else {
                    try {
                        var replicaCounts = getEventCounts(region);
                        var report = analyzeConsistency(primaryCounts, replicaCounts, primaryRegion, region);
                        var status = report.isDiverged ? "DIVERGED" : "OK";
                        allRegions.append(
                                String.format(
                                        "[%s %s] lag=%d ", region, status, Math.abs(report.eventDivergence)));
                    } catch (Exception e) {
                        allRegions.append(String.format("[%s ERROR] ", region));
                    }
                }
            }

            return allRegions.toString();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /** Report of consistency analysis. */
    private static class ConsistencyReport {

        String primaryRegion;
        String replicaRegion;
        long eventDivergence; // events ahead in primary
        long pendingDivergence; // pending events difference
        long offsetDivergence; // offset records difference
        boolean isDiverged;
    }
}
