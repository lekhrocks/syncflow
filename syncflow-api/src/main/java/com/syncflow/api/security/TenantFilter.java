package com.syncflow.api.security;

import com.syncflow.tenant.OrganizationId;
import com.syncflow.tenant.ProjectId;
import com.syncflow.tenant.TenantContext;
import com.syncflow.tenant.TenantContextHolder;
import com.syncflow.tenant.TenantId;
import com.syncflow.tenant.WorkspaceId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Component
public class TenantFilter extends OncePerRequestFilter {

    static final String TENANT_HEADER = "X-Tenant-Id";
    static final String ORG_HEADER = "X-Organization-Id";
    static final String WORKSPACE_HEADER = "X-Workspace-Id";
    static final String PROJECT_HEADER = "X-Project-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        try {
            var context = resolve(request);
            TenantContextHolder.set(context);
            chain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private TenantContext resolve(HttpServletRequest req) {
        var tenantId = req.getHeader(TENANT_HEADER) != null
                ? TenantId.from(req.getHeader(TENANT_HEADER))
                : TenantId.DEFAULT;
        var orgId = req.getHeader(ORG_HEADER);
        var wsId = req.getHeader(WORKSPACE_HEADER);
        var projId = req.getHeader(PROJECT_HEADER);

        String userId = "anonymous";
        Set<String> roles = new HashSet<>();

        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            userId = auth.getName() != null ? auth.getName() : userId;
            if (auth.getName() != null && auth.getName().startsWith("tenant:")) {
                var parts = auth.getName().substring("tenant:".length()).split(":");
                if (parts.length >= 4) {
                    tenantId = TenantId.from(parts[0]);
                    orgId = parts[1].isEmpty() ? orgId : parts[1];
                    wsId = parts[2].isEmpty() ? wsId : parts[2];
                    projId = parts[3].isEmpty() ? projId : parts[3];
                }
            }
            auth.getAuthorities().forEach(a -> roles.add(a.getAuthority()));
        }

        return new TenantContext(
                tenantId,
                orgId != null && !orgId.isEmpty() ? OrganizationId.from(orgId) : null,
                wsId != null && !wsId.isEmpty() ? WorkspaceId.from(wsId) : null,
                projId != null && !projId.isEmpty() ? ProjectId.from(projId) : null,
                userId, roles, Instant.now());
    }
}
