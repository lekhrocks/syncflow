package com.syncflow.tenant;

import java.time.Instant;
import java.util.Set;

public record TenantContext(
        TenantId tenantId,
        OrganizationId organizationId,
        WorkspaceId workspaceId,
        ProjectId projectId,
        String userId,
        Set<String> roles,
        Instant establishedAt) {
}
