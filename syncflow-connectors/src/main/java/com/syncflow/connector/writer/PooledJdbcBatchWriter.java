package com.syncflow.connector.writer;

import com.syncflow.core.model.ConnectionConfiguration;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HikariCP-backed connection pool for JDBC writers. Replaces the per-batch
 * {@code DriverManager.getConnection()} call that turned each write into a
 * fresh DB connection (the P2 bottleneck the architecture analysis flagged).
 *
 * The pool is keyed by the writer instance (the Spring bean is a singleton),
 * so one pool serves all pipelines on the same destination. The pool is built
 * lazily from the {@link ConnectionConfiguration} passed to
 * {@link #connect(ConnectionConfiguration)} — the jdbcUrl and credentials are
 * available at that point, so unlike the earlier dead implementation the pool
 * is always configured correctly before the first borrow.
 */
public abstract class PooledJdbcBatchWriter extends JdbcBatchWriter {

    private static final Logger log = LoggerFactory.getLogger(PooledJdbcBatchWriter.class);

    private static final Map<String, HikariDataSource> POOLS = new ConcurrentHashMap<>();
    // ponytail: System.getenv is read once at class load; tune via SYNCFLOW_WRITER_POOL_SIZE if needed
    private static final int POOL_SIZE = Integer.parseInt(
            System.getenv().getOrDefault("SYNCFLOW_WRITER_POOL_SIZE", "4"));

    private volatile String poolKey;

    /** Override to include the writer type in the pool key (e.g. "postgresql"). */
    protected String poolType() {
        return getClass().getSimpleName().toLowerCase();
    }

    @Override
    public void connect(ConnectionConfiguration config) {
        // Key the pool by the DESTINATION identity (jdbc url + credentials), not
        // a fixed connection type. Different destinations (e.g. separate test
        // containers with distinct random ports) must get separate pools; a
        // fixed per-type key would reuse one pool pointing at the wrong host.
        poolKey = poolType() + "|" + jdbcUrl(config) + "|" + config.username();
        POOLS.computeIfAbsent(poolKey, k -> {
            var hc = new HikariConfig();
            hc.setJdbcUrl(jdbcUrl(config));
            hc.setUsername(config.username());
            hc.setPassword(config.password());
            hc.setMaximumPoolSize(POOL_SIZE);
            hc.setMinimumIdle(0);
            // Each batch is a single executeBatch() and commits itself. The
            // explicit commit()/rollback() in the SPI become no-ops: a
            // multi-statement transaction would require flush+commit on the
            // SAME borrowed connection, which the pool model can't guarantee.
            // ponytail: single-batch auto-commit; multi-batch tx if needed later.
            hc.setAutoCommit(true);
            hc.setPoolName("syncflow-writer-" + poolKey);
            log.info("Writer pool created key={} url={} maxPoolSize={}",
                    poolKey, config.host() + ":" + config.port() + "/" + config.database(), POOL_SIZE);
            return new HikariDataSource(hc);
        });
    }

    @Override
    public Connection getConnection() throws SQLException {
        var pool = poolKey == null ? null : POOLS.get(poolKey);
        if (pool == null) {
            throw new SQLException("Writer pool not initialized — call connect(ConnectionConfiguration) first");
        }
        return pool.getConnection();
    }

    @Override
    protected Connection borrowConnection() throws SQLException {
        return getConnection();
    }

    @Override
    protected void returnConnection(Connection conn) {
        if (conn != null) {
            try {
                conn.close(); // returns the borrow to the pool
            } catch (SQLException e) {
                log.warn("Failed to return pooled connection key={}", poolKey, e);
            }
        }
    }

    @Override
    public boolean isConnected() {
        return POOLS.containsKey(poolKey);
    }

    @Override
    public void commit() {
        // Auto-commit per batch — nothing to do.
    }

    @Override
    public void rollback() {
        // Auto-commit per batch — nothing to roll back in the store. But
        // discard any buffered (not-yet-flushed) rows so a failed run's
        // residual buffer cannot leak into the next pipeline's destination.
        super.rollback();
    }

    @Override
    public void close() {
        // Pool is shared across pipelines on this destination; do NOT close it
        // per write. Borrowed connections are returned to the pool.
        log.debug("Writer close() for poolKey={} (pool kept open)", poolKey);
    }

    /** Close all pools (JVM shutdown / tests). */
    public static void closeAllPools() {
        POOLS.values().forEach(HikariDataSource::close);
        POOLS.clear();
    }
}

/**
 * Spring lifecycle hook that closes every writer pool on application shutdown,
 * so Hikari connections are released instead of leaking until the JVM exits.
 */
@Component
class PoolShutdownHook implements DisposableBean {

    @Override
    public void destroy() {
        PooledJdbcBatchWriter.closeAllPools();
    }
}
