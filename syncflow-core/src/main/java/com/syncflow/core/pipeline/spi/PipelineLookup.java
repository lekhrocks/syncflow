package com.syncflow.core.pipeline.spi;

import com.syncflow.core.pipeline.PipelineDesign;
import com.syncflow.tenant.TenantContext;

/** Outbound port for tenant-scoped pipeline lookup by runtime orchestration. */
@FunctionalInterface
public interface PipelineLookup {

    PipelineDesign get(String pipelineId, TenantContext tenantContext);
}
