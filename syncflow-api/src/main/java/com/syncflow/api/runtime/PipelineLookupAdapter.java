package com.syncflow.api.runtime;

import com.syncflow.api.pipeline.PipelineDesignerService;
import com.syncflow.core.pipeline.PipelineDesign;
import com.syncflow.core.pipeline.spi.PipelineLookup;
import com.syncflow.tenant.TenantContext;
import org.springframework.stereotype.Component;

/** Adapter exposing tenant-aware pipeline lookup to runtime ports. */
@Component
public final class PipelineLookupAdapter implements PipelineLookup {

    private final PipelineDesignerService delegate;

    public PipelineLookupAdapter(PipelineDesignerService delegate) {
        this.delegate = delegate;
    }

    @Override
    public PipelineDesign get(String pipelineId, TenantContext tenantContext) {
        return delegate.get(pipelineId, tenantContext);
    }
}
