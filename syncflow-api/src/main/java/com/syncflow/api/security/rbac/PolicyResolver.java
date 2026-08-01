package com.syncflow.api.security.rbac;

import com.syncflow.tenant.TenantId;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

@Component
public class PolicyResolver {

    public EnumSet<ResourcePermission> resolve(TenantId tenantId, String userId) {
        EnumSet<ResourcePermission> permissions = EnumSet.of(ResourcePermission.METRICS_READ,
                ResourcePermission.PIPELINE_READ, ResourcePermission.CONNECTION_READ);

        permissions.addAll(ResourcePermission.developer());
        if (userId != null && userId.equals("admin")) {
            permissions.addAll(ResourcePermission.workspaceAdmin());
        }

        return permissions;
    }
}
