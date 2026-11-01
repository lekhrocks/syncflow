package com.syncflow.api.runtime;

import com.syncflow.api.lock.DistributedLockService;
import com.syncflow.core.runtime.RuntimeLock;
import com.syncflow.tenant.TenantContext;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** Adapts the Postgres advisory lock to the runtime port. */
@Component
public final class PostgresRuntimeLockAdapter implements RuntimeLock {

    private final DistributedLockService delegate;

    public PostgresRuntimeLockAdapter(DistributedLockService delegate) {
        this.delegate = delegate;
    }

    @Override
    public <T> T withLock(String name, TenantContext tenantContext, Duration timeout, Supplier<T> action) {
        return delegate.withLock(name, tenantContext, timeout, action);
    }
}
