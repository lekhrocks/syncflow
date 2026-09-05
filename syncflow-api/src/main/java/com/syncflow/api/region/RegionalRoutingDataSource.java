package com.syncflow.api.region;

import java.sql.Connection;
import java.sql.SQLException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.stereotype.Component;

/**
 * Spring Data JPA routing datasource that delegates to
 * RegionalDataSourceFactory.
 *
 * <p>
 * Integrates multi-region datasource management with JPA repositories.
 * Automatically routes:
 * - Writes (INSERT/UPDATE/DELETE/DDL) to primary region datasource
 * - Reads (SELECT) to local region replica (with fallback to primary)
 *
 * <p>
 * This bean is only instantiated when syncflow.region.replication-enabled is
 * true.
 * In single-region deployments (the default), this bean is skipped, and the
 * standard Spring
 * datasource is used instead.
 *
 * <p>
 * Usage: Configure PersistenceConfig to use this datasource for JPA instead of
 * a single
 * datasource. Spring will invoke determineCurrentLookupKey() before each
 * operation to select
 * the correct pool.
 */
@Component
@ConditionalOnProperty(name = "syncflow.region.replication-enabled", havingValue = "true", matchIfMissing = false)
public class RegionalRoutingDataSource extends AbstractRoutingDataSource {

    private final RegionalDataSourceFactory regionalDataSourceFactory;
    private final RegionalProperties regionalProperties;

    public RegionalRoutingDataSource(
            RegionalDataSourceFactory regionalDataSourceFactory,
            RegionalProperties regionalProperties) {
        this.regionalDataSourceFactory = regionalDataSourceFactory;
        this.regionalProperties = regionalProperties;

        // Set default datasource (fallback if no routing key determined)
        setDefaultTargetDataSource(
                regionalDataSourceFactory.getPrimaryDataSource());
    }

    /**
     * Determine which datasource to use for the current operation.
     *
     * <p>
     * Spring Data JPA invokes this method before each SQL operation (via
     * AbstractRoutingDataSource.getConnection()). Returns a key that identifies
     * which datasource
     * to use.
     *
     * @return datasource key ("primary" or "local_replica")
     */
    @Override
    protected Object determineCurrentLookupKey() {
        // Check if this is a write operation (always go to primary)
        var readOnly = isReadOnlyOperation();
        if (!readOnly) {
            return "primary";
        }

        // For reads, try to use local replica; fallback to primary if unavailable
        return "local_replica";
    }

    /**
     * Override getConnection() to implement the actual routing logic with
     * HikariDataSource
     * selection.
     *
     * <p>
     * AbstractRoutingDataSource uses determineCurrentLookupKey() to index into a
     * targetDataSources
     * map, but we need dynamic datasource selection from RegionalDataSourceFactory.
     * Instead, we
     * directly return the appropriate connection.
     */
    @Override
    public Connection getConnection() throws SQLException {
        var readOnly = isReadOnlyOperation();
        if (readOnly) {
            return regionalDataSourceFactory
                    .getReadDataSource(regionalProperties.getLocalRegion())
                    .getConnection();
        } else {
            return regionalDataSourceFactory.getPrimaryDataSource().getConnection();
        }
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        // Ignore username/password override; use configured credentials from datasource
        // config
        return getConnection();
    }

    /**
     * Detect if current transaction/operation is read-only.
     *
     * <p>
     * Checks:
     * 1. Spring's @Transactional(readOnly=true) annotation
     * 2. Transaction status from TransactionSynchronizationManager
     * 3. Conservative fallback: if cannot determine, assume write (send to primary)
     *
     * @return true if read-only; false if write or unknown
     */
    private boolean isReadOnlyOperation() {
        // Check Spring transaction metadata
        try {
            var txn = org.springframework.transaction.support.TransactionSynchronizationManager
                    .isCurrentTransactionReadOnly();
            if (txn) {
                return true;
            }
        } catch (Exception ignored) {
            // Ignore; not in a transaction context
        }

        // Default: assume write operation (conservative, sends to primary)
        return false;
    }

    /**
     * Graceful shutdown: close all underlying datasources.
     */
    public void shutdown() {
        try {
            regionalDataSourceFactory.shutdown();
        } catch (Exception e) {
            logger.warn("Error during regional datasource shutdown", e);
        }
    }

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RegionalRoutingDataSource.class);
}
