package com.syncflow.api.runtime;

import com.syncflow.api.connection.service.ConnectionService;
import com.syncflow.core.connection.spi.ConnectionLookup;
import com.syncflow.core.model.ConnectionConfiguration;
import com.syncflow.tenant.TenantContext;
import org.springframework.stereotype.Component;

/** Adapter exposing the tenant-aware connection service to runtime ports. */
@Component
public final class ConnectionLookupAdapter implements ConnectionLookup {

    private final ConnectionService delegate;

    public ConnectionLookupAdapter(ConnectionService delegate) {
        this.delegate = delegate;
    }

    @Override
    public ConnectionConfiguration getWithDecryptedCredentials(String connectionId,
            TenantContext tenantContext) {
        return delegate.getWithDecryptedCredentials(connectionId, tenantContext);
    }
}
