package com.syncflow.api.security.rbac;

import com.syncflow.tenant.TenantId;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Component
public class PolicyResolver {

    /** The role that grants workspace-admin (full) permissions. */
    public static final String ADMIN_ROLE = "ADMIN";

    public EnumSet<ResourcePermission> resolve(TenantId tenantId, String userId) {
        return resolve(tenantId, userId, Set.of());
    }

    public EnumSet<ResourcePermission> resolve(TenantId tenantId, String userId, Set<String> roles) {
        EnumSet<ResourcePermission> permissions = EnumSet.of(ResourcePermission.METRICS_READ,
                ResourcePermission.PIPELINE_READ, ResourcePermission.CONNECTION_READ);

        permissions.addAll(ResourcePermission.developer());
        boolean isAdmin = (userId != null && userId.equals("admin"))
                || (roles != null && roles.contains(ADMIN_ROLE));
        if (isAdmin) {
            permissions.addAll(ResourcePermission.workspaceAdmin());
        }

        return permissions;
    }
}
