package com.syncflow.api.security.rbac;

import com.syncflow.tenant.TenantContext;
import com.syncflow.tenant.TenantContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
public class AuthorizationService {

    private final PolicyResolver policyResolver;

    public AuthorizationService(PolicyResolver policyResolver) {
        this.policyResolver = policyResolver;
    }

    public void require(ResourcePermission permission) {
        var ctx = TenantContextHolder.get();
        if (ctx == null)
            throw new AccessDeniedException("No tenant context");
        if (!isPermitted(permission, ctx)) {
            throw new AccessDeniedException("Missing permission: " + permission);
        }
    }

    public boolean isPermitted(ResourcePermission permission, TenantContext ctx) {
        var policies = policyResolver.resolve(ctx.tenantId(), ctx.userId(), ctx.roles());
        return policies.contains(permission);
    }

    public boolean isPermitted(ResourcePermission permission) {
        var ctx = TenantContextHolder.get();
        return ctx != null && isPermitted(permission, ctx);
    }

    public void requireAny(Collection<ResourcePermission> permissions) {
        if (permissions.stream().noneMatch(this::isPermitted)) {
            throw new AccessDeniedException("Missing any of: " + permissions);
        }
    }
}
