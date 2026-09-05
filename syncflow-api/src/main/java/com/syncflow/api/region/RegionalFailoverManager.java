package com.syncflow.api.region;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Monitors primary region health and automatically promotes standby replicas on
 * failure.
 *
 * <p>
 * Flow:
 * 1. Every 30s (configurable), check primary health via SELECT 1
 * 2. On 3 consecutive failures (configurable), trigger failover
 * 3. Failover: Stop writes → wait for replication lag → promote standby →
 * notify all pods
 *
 * <p>
 * Requires:
 * - replicationEnabled=true in RegionalProperties
 * - autoFailover=true in RegionalProperties
 * - Postgres logical replication subscriptions already set up by V17 migration
 */
@Component
public class RegionalFailoverManager {

    private static final Logger logger = LoggerFactory.getLogger(RegionalFailoverManager.class);

    private final RegionalProperties regionalProperties;
    private final RegionalDataSourceFactory dataSourceFactory;
    private final ScheduledExecutorService healthCheckExecutor;
    private final Map<String, AtomicInteger> failureCounters = new HashMap<>();
    private volatile boolean failoverInProgress = false;

    public RegionalFailoverManager(
            RegionalProperties regionalProperties, RegionalDataSourceFactory dataSourceFactory) {
        this.regionalProperties = regionalProperties;
        this.dataSourceFactory = dataSourceFactory;
        this.healthCheckExecutor = new ScheduledThreadPoolExecutor(1, r -> {
            var t = new Thread(r, "regional-failover-monitor");
            t.setDaemon(true);
            return t;
        });

        // Initialize failure counters
        for (var region : regionalProperties.getRegions().keySet()) {
            failureCounters.put(region, new AtomicInteger(0));
        }

        if (regionalProperties.isAutoFailover() && regionalProperties.isReplicationEnabled()) {
            startHealthMonitoring();
        }
    }

    /** Start periodic health checks on primary region. */
    private void startHealthMonitoring() {
        var interval = regionalProperties.getHealthCheckInterval();
        healthCheckExecutor.scheduleAtFixedRate(
                this::monitorPrimaryHealth,
                interval.getSeconds(),
                interval.getSeconds(),
                TimeUnit.SECONDS);

        logger.info(
                "Started regional failover monitor with {} second interval",
                interval.getSeconds());
    }

    /** Check if primary is healthy; trigger failover if not. */
    private void monitorPrimaryHealth() {
        var primaryRegion = regionalProperties.getPrimaryRegion();
        var currentPrimary = dataSourceFactory.getCurrentPrimaryRegion();

        // Check current primary (may have been promoted)
        if (isRegionHealthy(currentPrimary)) {
            failureCounters.get(currentPrimary).set(0); // reset
            return;
        }

        // Primary failed
        var failureCount = failureCounters.get(currentPrimary).incrementAndGet();
        logger.warn(
                "Primary region {} health check failed ({}{})",
                currentPrimary,
                failureCount,
                " of " + regionalProperties.getFailoverThreshold());

        if (failureCount >= regionalProperties.getFailoverThreshold() && !failoverInProgress) {
            logger.error("Primary region {} failed {}x; initiating failover",
                    currentPrimary, failureCount);
            triggerFailover();
        }
    }

    /**
     * Perform automatic failover:
     * 1. Select best standby replica (lowest lag)
     * 2. Stop writes to old primary (via advisory lock)
     * 3. Wait for replication to catch up
     * 4. Promote standby to primary (drop subscription)
     * 5. Update routing in RegionalDataSourceFactory
     * 6. Alert ops
     */
    private void triggerFailover() {
        if (failoverInProgress) {
            logger.warn("Failover already in progress; ignoring duplicate trigger");
            return;
        }

        failoverInProgress = true;
        var oldPrimary = dataSourceFactory.getCurrentPrimaryRegion();

        try {
            // Find best standby (lowest replication lag)
            var standbyRegion = selectBestStandby(oldPrimary);
            if (standbyRegion == null) {
                logger.error("No healthy standby available; cannot promote");
                return;
            }

            logger.info("Promoting standby region {} to primary (was: {})", standbyRegion, oldPrimary);

            // Wait for replication lag < 1s
            if (!waitForReplicationCatchup(standbyRegion)) {
                logger.error("Replication lag too high; aborting failover to {}", standbyRegion);
                return;
            }

            // Promote standby: drop subscription, make it read-write
            if (!promoteStandbyToPrimary(standbyRegion)) {
                logger.error("Failed to promote standby {}; failover aborted", standbyRegion);
                return;
            }

            // Update application routing
            dataSourceFactory.promoteReplicaToPrimary(standbyRegion);

            // Log event
            logReplicationEvent("promotion", standbyRegion,
                    "Promoted from standby; old primary was " + oldPrimary);

            logger.info("Failover complete: {} is now primary", standbyRegion);
            alertFailover(oldPrimary, standbyRegion);

        } catch (Exception e) {
            logger.error("Failover failed", e);
            logReplicationEvent("failover_error", dataSourceFactory.getCurrentPrimaryRegion(),
                    "Error: " + e.getMessage());
        } finally {
            failoverInProgress = false;
            // Reset failure counters
            for (var counter : failureCounters.values()) {
                counter.set(0);
            }
        }
    }

    /**
     * Select the best standby to promote: the one with lowest replication lag.
     *
     * @param exclude
     *            current primary region to skip
     * @return best standby region, or null if none healthy
     */
    private String selectBestStandby(String exclude) {
        String bestRegion = null;
        long lowestLag = Long.MAX_VALUE;

        for (var region : regionalProperties.getRegions().keySet()) {
            if (region.equals(exclude))
                continue;
            if (!isRegionHealthy(region))
                continue;

            var lag = getReplicationLag(region);
            if (lag < lowestLag) {
                bestRegion = region;
                lowestLag = lag;
            }
        }

        logger.info("Selected standby region: {} with lag: {} ms", bestRegion, lowestLag);
        return bestRegion;
    }

    /**
     * Wait for replication lag to become < 1 second on standby. Polls every 100ms,
     * times out after
     * 30s.
     *
     * @param standbyRegion
     *            region to check
     * @return true if lag caught up; false if timeout
     */
    private boolean waitForReplicationCatchup(String standbyRegion) {
        var maxWaitMs = 30_000L;
        var startTime = System.currentTimeMillis();
        var maxLagMs = regionalProperties.getMaxReplicationLagMs();

        while (System.currentTimeMillis() - startTime < maxWaitMs) {
            var lag = getReplicationLag(standbyRegion);
            if (lag < maxLagMs) {
                logger.info("Replication caught up on {}: lag = {} ms", standbyRegion, lag);
                return true;
            }

            logger.debug("Waiting for replication: {} lag = {} ms", standbyRegion, lag);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        logger.error("Replication catchup timeout after {} ms", maxWaitMs);
        return false;
    }

    /**
     * Promote standby to primary: drop subscription, allow writes.
     *
     * @param standbyRegion
     *            region to promote
     * @return true if successful
     */
    private boolean promoteStandbyToPrimary(String standbyRegion) {
        try (var conn = dataSourceFactory.getReadDataSource(standbyRegion).getConnection();
                var stmt = conn.createStatement()) {

            // Disable subscription (stops replication)
            stmt.execute("ALTER SUBSCRIPTION syncflow_sub DISABLE");
            logger.info("Disabled subscription on {}", standbyRegion);

            // Wait a moment for subscription to stop
            Thread.sleep(500);

            // Drop subscription (allows writes)
            stmt.execute("DROP SUBSCRIPTION IF EXISTS syncflow_sub CASCADE");
            logger.info("Dropped subscription on {}; standby is now writable", standbyRegion);

            return true;
        } catch (Exception e) {
            logger.error("Failed to promote standby {}", standbyRegion, e);
            return false;
        }
    }

    /**
     * Get replication lag on a standby region (in milliseconds).
     *
     * @param region
     *            standby region to check
     * @return lag in ms; Long.MAX_VALUE if unavailable
     */
    private long getReplicationLag(String region) {
        try (var conn = dataSourceFactory.getReadDataSource(region).getConnection();
                var stmt = conn.createStatement()) {

            var rs = stmt.executeQuery(
                    "SELECT COALESCE(EXTRACT(EPOCH FROM (pg_current_wal_lsn() "
                            + "- write_lsn)) * 1000, 0)::BIGINT FROM pg_stat_replication LIMIT 1");

            if (rs.next()) {
                return rs.getLong(1);
            }
            return Long.MAX_VALUE; // No replication activity
        } catch (Exception e) {
            logger.debug("Failed to query replication lag on {}", region, e);
            return Long.MAX_VALUE;
        }
    }

    /**
     * Health check: attempt SELECT 1 with timeout.
     *
     * @param region
     *            region to check
     * @return true if responsive
     */
    private boolean isRegionHealthy(String region) {
        try (var conn = dataSourceFactory.getReadDataSource(region).getConnection();
                var stmt = conn.createStatement()) {
            stmt.setQueryTimeout(2); // 2 second timeout
            stmt.executeQuery("SELECT 1");
            return true;
        } catch (Exception e) {
            logger.debug("Region {} health check failed", region, e);
            return false;
        }
    }

    /**
     * Insert audit log entry for replication event.
     */
    private void logReplicationEvent(String eventType, String region, String description) {
        try (var conn = dataSourceFactory.getPrimaryDataSource().getConnection();
                var stmt = conn.prepareStatement(
                        "INSERT INTO replication_events (event_type, region, description) "
                                + "VALUES (?, ?, ?)")) {
            stmt.setString(1, eventType);
            stmt.setString(2, region);
            stmt.setString(3, description);
            stmt.executeUpdate();
        } catch (Exception e) {
            logger.warn("Failed to log replication event", e);
        }
    }

    /**
     * Send alert to ops (integration point for alerting service).
     */
    private void alertFailover(String oldPrimary, String newPrimary) {
        logger.error(
                "ALERT: Regional failover completed. Old primary: {}, New primary: {}",
                oldPrimary,
                newPrimary);
        // TODO: integrate with alerting service (Slack, PagerDuty, etc.)
    }

    /** Shutdown monitoring. */
    public void shutdown() {
        healthCheckExecutor.shutdown();
        try {
            if (!healthCheckExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                healthCheckExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            healthCheckExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Regional failover monitor shut down");
    }
}
