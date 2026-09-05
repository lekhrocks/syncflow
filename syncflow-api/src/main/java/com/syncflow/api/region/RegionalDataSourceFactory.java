package com.syncflow.api.region;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Factory for creating regional DataSources with failover awareness.
 * Routes writes to primary region; reads can use local replicas when available.
 *
 * <p>
 * Usage:
 * - For writes: always use primary datasource
 * - For reads: use local replica if available, fallback to primary
 * - During failover: updatePrimaryRegion() switches to new primary
 */
@Component
public class RegionalDataSourceFactory {

    private static final Logger logger = LoggerFactory.getLogger(RegionalDataSourceFactory.class);

    private final RegionalProperties regionalProperties;
    private final Map<String, HikariDataSource> datasources = new ConcurrentHashMap<>();
    private volatile String currentPrimaryRegion;

    public RegionalDataSourceFactory(RegionalProperties regionalProperties) {
        this.regionalProperties = regionalProperties;
        this.currentPrimaryRegion = regionalProperties.getPrimaryRegion();
        initializeDataSources();
    }

    private void initializeDataSources() {
        for (var entry : regionalProperties.getRegions().entrySet()) {
            var region = entry.getKey();
            var config = entry.getValue();
            try {
                var ds = createDataSource(region, config.getConnectionString());
                datasources.put(region, ds);
                logger.info("Initialized datasource for region: {}", region);
            } catch (Exception e) {
                logger.error("Failed to initialize datasource for region: {}", region, e);
            }
        }
    }

    /**
     * Get datasource for primary region (write-capable). Used for
     * INSERT/UPDATE/DELETE/DDL.
     *
     * @return HikariDataSource for current primary region
     * @throws IllegalStateException
     *             if primary datasource unavailable
     */
    public HikariDataSource getPrimaryDataSource() {
        var ds = datasources.get(currentPrimaryRegion);
        if (ds == null) {
            throw new IllegalStateException(
                    "Primary datasource unavailable for region: " + currentPrimaryRegion);
        }
        return ds;
    }

    /**
     * Get datasource for local region, preferring read replicas. Falls back to
     * primary on unavailable
     * replica.
     *
     * @param localRegion
     *            region to read from (e.g., "eu-west-1")
     * @return HikariDataSource for local region, or primary if local unavailable
     */
    public HikariDataSource getReadDataSource(String localRegion) {
        // If local region is primary, always use primary
        if (localRegion.equals(currentPrimaryRegion)) {
            return getPrimaryDataSource();
        }

        // Check if local replica is available
        var ds = datasources.get(localRegion);
        if (ds != null && ds.isRunning()) {
            logger.debug("Using read replica in region: {}", localRegion);
            return ds;
        }

        logger.warn(
                "Read replica unavailable in {}, falling back to primary", localRegion);
        return getPrimaryDataSource();
    }

    /**
     * Promote standby replica to primary after failover. Call this from
     * RegionalFailoverManager.
     *
     * <p>
     * This updates routing but does NOT perform database-level promotion (that's
     * handled in
     * RegionalFailoverManager after advisory lock + subscription drop).
     *
     * @param newPrimaryRegion
     *            region to promote (e.g., "eu-west-1")
     */
    public synchronized void promoteReplicaToPrimary(String newPrimaryRegion) {
        if (!datasources.containsKey(newPrimaryRegion)) {
            throw new IllegalArgumentException("Region not configured: " + newPrimaryRegion);
        }

        logger.info(
                "Promoting region {} to primary (was: {})",
                newPrimaryRegion,
                currentPrimaryRegion);
        currentPrimaryRegion = newPrimaryRegion;
    }

    /**
     * Update datasource for a region (e.g., after connection string changes).
     * Closes old datasource
     * and creates new one.
     *
     * @param region
     *            region to update
     * @param connectionString
     *            new connection string
     */
    public synchronized void updateRegionalDataSource(String region, String connectionString) {
        // Close old datasource
        var old = datasources.get(region);
        if (old != null && !old.isClosed()) {
            try {
                old.close();
                logger.info("Closed datasource for region: {}", region);
            } catch (Exception e) {
                logger.warn("Error closing datasource for region: {}", region, e);
            }
        }

        // Create new datasource
        try {
            var newDs = createDataSource(region, connectionString);
            datasources.put(region, newDs);
            logger.info("Updated datasource for region: {} with new connection", region);
        } catch (Exception e) {
            logger.error("Failed to update datasource for region: {}", region, e);
            throw new RuntimeException("Cannot update datasource for " + region, e);
        }
    }

    /**
     * Graceful shutdown: close all datasources.
     */
    public void shutdown() {
        for (var entry : datasources.entrySet()) {
            var region = entry.getKey();
            var ds = entry.getValue();
            try {
                if (ds != null && !ds.isClosed()) {
                    ds.close();
                    logger.info("Closed datasource for region: {}", region);
                }
            } catch (Exception e) {
                logger.warn("Error closing datasource for region: {}", region, e);
            }
        }
    }

    /**
     * Check if a datasource is healthy by attempting a simple query. Used by health
     * monitors.
     *
     * @param region
     *            region to check
     * @return true if datasource is healthy
     */
    public boolean isHealthy(String region) {
        var ds = datasources.get(region);
        if (ds == null || ds.isClosed()) {
            return false;
        }

        try (var conn = ds.getConnection();
                var stmt = conn.createStatement()) {
            stmt.executeQuery("SELECT 1");
            return true;
        } catch (Exception e) {
            logger.warn("Datasource health check failed for region: {}", region, e);
            return false;
        }
    }

    /**
     * Get current primary region. Useful for logging/debugging.
     *
     * @return region name (e.g., "us-east-1")
     */
    public String getCurrentPrimaryRegion() {
        return currentPrimaryRegion;
    }

    /**
     * Create a HikariCP datasource with region-specific timeout settings.
     */
    private HikariDataSource createDataSource(String region, String connectionString) {
        var config = regionalProperties.getRegionConfig(region);
        var hikariConfig = new HikariConfig();

        hikariConfig.setJdbcUrl(connectionString);
        hikariConfig.setPoolName("syncflow-" + region);
        hikariConfig.setMaximumPoolSize(20);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setConnectionTimeout(config.getConnectionTimeoutMs());
        hikariConfig.setIdleTimeout(600000); // 10 minutes
        hikariConfig.setMaxLifetime(1800000); // 30 minutes
        hikariConfig.setLeakDetectionThreshold(60000); // 1 minute

        // Statement timeout in seconds; convert from ms
        var statementTimeoutSec = (config.getStatementTimeoutMs() + 999) / 1000;
        hikariConfig.addDataSourceProperty("statement_timeout", statementTimeoutSec * 1000);

        // Connection-level timeout for validity checks
        hikariConfig.setConnectionTestQuery("SELECT 1");

        return new HikariDataSource(hikariConfig);
    }
}
