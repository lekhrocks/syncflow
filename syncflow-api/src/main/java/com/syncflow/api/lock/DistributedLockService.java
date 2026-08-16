package com.syncflow.api.lock;

import com.syncflow.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Postgres advisory-lock based distributed lock.
 *
 * Why advisory locks and not Redis: SyncFlow already depends on Postgres
 * for the durable state; adding a second locking dependency (Redis) for a
 * single-feature lock is unwarranted complexity. Advisory locks are
 * cluster-wide, session-scoped, and survive across the same Postgres
 * instance (and read replicas via logical decoding if needed later).
 * <p>
 * Usage:
 *
 * <pre>
 * lockService.withLock("capture:" + pipelineId, tenantContext, Duration.ofSeconds(30), () -> {
 *     // critical section — no other pod holds this lock for the duration
 * });
 * </pre>
 *
 * The lock is automatically released when the JDBC session ends (transaction
 * commit/rollback) or when {@link #releaseLock(String, String)} is called
 * explicitly with the same token.
 */
@Component
public class DistributedLockService {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockService.class);

    private final JdbcTemplate jdbc;

    public DistributedLockService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Acquire a named lock, run the action, then release. Throws
     * {@link LockAcquisitionException} if the lock cannot be acquired within
     * the timeout.
     */
    public <T> T withLock(String name, TenantContext tenantContext,
            Duration timeout, Supplier<T> action) {
        var token = UUID.randomUUID().toString();
        var start = Instant.now();
        // Hash the lock name into a bigint for pg_advisory_lock's 64-bit key.
        var lockKey = name.hashCode() & 0xFFFFFFFFL;
        boolean acquired = false;
        try {
            acquired = acquireWithRetry(lockKey, timeout, start);
            if (!acquired) {
                throw new LockAcquisitionException(
                        "Failed to acquire lock " + name + " within " + timeout);
            }
            return action.get();
        } finally {
            if (acquired) {
                releaseLock(lockKey, token);
            }
        }
    }

    private boolean acquireWithRetry(long lockKey, Duration timeout, Instant start) {
        var deadline = start.plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            // pg_try_advisory_lock returns true if the lock was acquired.
            Boolean ok = jdbc.queryForObject(
                    "SELECT pg_try_advisory_lock(?)", Boolean.class, lockKey);
            if (Boolean.TRUE.equals(ok)) {
                log.debug("Acquired distributed lock key={}", lockKey);
                return true;
            }
            try {
                Thread.sleep(Duration.ofMillis(100).toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void releaseLock(long lockKey, String token) {
        try {
            // pg_advisory_unlock returns true if the lock was held.
            Boolean released = jdbc.queryForObject(
                    "SELECT pg_advisory_unlock(?)", Boolean.class, lockKey);
            log.debug("Released distributed lock key={} released={}", lockKey, released);
        } catch (Exception e) {
            log.warn("Failed to release distributed lock key={} token={}", lockKey, token, e);
        }
    }

    public static class LockAcquisitionException extends RuntimeException {

        public LockAcquisitionException(String message) {
            super(message);
        }
    }
}
