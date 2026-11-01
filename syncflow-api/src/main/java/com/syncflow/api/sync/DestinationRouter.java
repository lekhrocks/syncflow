package com.syncflow.api.sync;

import com.syncflow.core.cdc.CDCEvent;
import com.syncflow.core.connection.spi.ConnectionLookup;
import com.syncflow.core.spi.writer.WriterRegistry;
import com.syncflow.tenant.TenantContext;
import com.syncflow.tenant.TenantContextHolder;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * API compatibility adapter for the transport-neutral runtime router.
 *
 * <p>
 * Every method takes {@link TenantContext} explicitly and forwards it to the
 * runtime router. This adapter deliberately does <em>not</em> read
 * {@link TenantContextHolder}: its production caller is the
 * CDC worker, which runs on a virtual thread where the ThreadLocal is unset by
 * design (see {@code SyncOrchestrator.run()}). Reading the holder here would
 * silently resolve every batch under the system tenant, so the tenant must
 * travel as a parameter instead.
 */
@Component
public class DestinationRouter {

    // FQCN: the runtime router deliberately shares this class's simple name, and
    // the enclosing type shadows any import of it — Java has no import aliasing.
    private final com.syncflow.runtime.sync.DestinationRouter delegate;

    public DestinationRouter(WriterRegistry writerRegistry, ConnectionLookup connectionLookup) {
        this.delegate = new com.syncflow.runtime.sync.DestinationRouter(writerRegistry,
                connectionLookup);
    }

    /**
     * Writes a batch of CDC events to the destination connection resolved under
     * {@code tenantContext}.
     *
     * @param tenantContext
     *            the tenant whose connection is looked up; must not be
     *            {@code null} — pass the worker's context, never
     *            {@link TenantContext#system()} as a fallback
     */
    public WriteResult writeBatch(String connectionId, List<CDCEvent> events,
            List<String> destColumns, TenantContext tenantContext) {
        var result = delegate.writeBatch(connectionId, events, destColumns, tenantContext);
        return new WriteResult(result.success(), result.error());
    }

    /** Legacy single-event convenience path; delegates to {@link #writeBatch}. */
    public WriteResult write(String connectionId, CDCEvent event, List<String> destColumns,
            TenantContext tenantContext) {
        return writeBatch(connectionId, List.of(event), destColumns, tenantContext);
    }

    public record WriteResult(boolean success, String error) {
    }
}
