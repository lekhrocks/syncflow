package com.syncflow.api.security.rbac;

import com.syncflow.tenant.TenantContext;
import com.syncflow.tenant.TenantContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
public class AuthorizationService {

    /**
     * INTENTIONAL THREADLOCAL READ.
     *
     * After the P0/F1 refactor, the only place that reads
     * {@link TenantContextHolder}
     * on a code path that runs on the request thread is this class. Every other
     * orchestrator/service takes {@link TenantContext} as a method parameter so
     * background workers don't depend on the ThreadLocal.
     *
     * If you are adding a new code path that wants to read the current tenant,
     * prefer passing {@link TenantContext} as a parameter. Only this class is
     * allowed to read the ThreadLocal.
     */

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
