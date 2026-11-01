package com.syncflow.core.connection.spi;

import com.syncflow.core.model.ConnectionConfiguration;
import com.syncflow.tenant.TenantContext;

/**
 * Outbound port used by runtime orchestration to resolve a destination
 * connection without depending on an API service or persistence adapter.
 */
@FunctionalInterface
public interface ConnectionLookup {

    ConnectionConfiguration getWithDecryptedCredentials(String connectionId, TenantContext tenantContext);
}
