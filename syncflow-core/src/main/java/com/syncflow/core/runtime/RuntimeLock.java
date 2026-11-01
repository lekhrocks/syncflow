package com.syncflow.core.runtime;

import com.syncflow.tenant.TenantContext;
import java.time.Duration;
import java.util.function.Supplier;

/** Outbound port for cluster coordination used by runtime orchestration. */
@FunctionalInterface
public interface RuntimeLock {

    <T> T withLock(String name, TenantContext tenantContext, Duration timeout, Supplier<T> action);
}
