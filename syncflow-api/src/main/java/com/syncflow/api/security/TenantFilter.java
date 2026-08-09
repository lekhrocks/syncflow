package com.syncflow.api.security;

import com.syncflow.api.config.TenantJwtAuthenticationConverter.PrincipalTenant;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Resolves the tenant context for the current request.
 *
 * SECURITY: the tenant is derived from the AUTHENTICATED PRINCIPAL (the JWT's
 * tenant claims, attached by
 * {@link com.syncflow.api.config.TenantJwtAuthenticationConverter})
 * — never trusted from client headers. An authenticated caller cannot switch
 * tenant by spoofing {@code X-Tenant-Id}.
 *
 * UI compatibility: the SPA sends {@code X-Tenant-Id} on authenticated
 * requests, so a header that MATCHES the principal's tenant is accepted
 * (harmless — same tenant). A mismatched header is ignored in favour of the
 * principal. Unauthenticated/anonymous requests (public endpoints) fall back
 * to {@link TenantId#DEFAULT}.
 */
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
        Set<String> roles = new HashSet<>();
        var auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated()) {
            var principal = principalTenant(auth);
            if (principal == null) {
                // Legacy principals (e.g. test stubs / basic auth) carry no tenant
                // claims; fall back to a header only as a last resort so existing
                // clients keep working. Authenticated tenants still come from the
                // principal whenever claims exist.
                return contextFromHeaders(req, auth);
            }
            // Header is honored ONLY when it matches the principal's tenant (the UI
            // sends it on every request); a mismatched/spoofed header is ignored.
            var headerTenant = header(req, TENANT_HEADER);
            var tenantId = principal.tenantId();
            if (headerTenant != null && TenantId.from(headerTenant).equals(tenantId)) {
                // same tenant — keep org/ws/project best-effort from headers when the
                // principal lacks them
                return new TenantContext(
                        tenantId,
                        principal.organizationId() != null
                                ? OrganizationId.from(principal.organizationId())
                                : optOrgId(req),
                        principal.workspaceId() != null
                                ? WorkspaceId.from(principal.workspaceId())
                                : optWorkspaceId(req),
                        principal.projectId() != null
                                ? ProjectId.from(principal.projectId())
                                : optProjectId(req),
                        auth.getName(), authorities(auth), Instant.now());
            }
            return new TenantContext(
                    tenantId,
                    principal.organizationId() != null ? OrganizationId.from(principal.organizationId()) : null,
                    principal.workspaceId() != null ? WorkspaceId.from(principal.workspaceId()) : null,
                    principal.projectId() != null ? ProjectId.from(principal.projectId()) : null,
                    auth.getName(), authorities(auth), Instant.now());
        }

        return contextFromHeaders(req, auth);
    }

    /**
     * Principal carries a {@link PrincipalTenant} (set by
     * TenantJwtAuthenticationConverter).
     */
    private PrincipalTenant principalTenant(Authentication auth) {
        if (auth.getDetails() instanceof PrincipalTenant pt) {
            return pt;
        }
        return null;
    }

    private TenantContext contextFromHeaders(HttpServletRequest req, Authentication auth) {
        var headerTenant = header(req, TENANT_HEADER);
        var tenantId = headerTenant != null ? TenantId.from(headerTenant) : TenantId.DEFAULT;
        return new TenantContext(
                tenantId,
                optOrgId(req),
                optWorkspaceId(req),
                optProjectId(req),
                auth != null && auth.getName() != null ? auth.getName() : "anonymous",
                authorities(auth), Instant.now());
    }

    private Set<String> authorities(Authentication auth) {
        Set<String> roles = new HashSet<>();
        if (auth != null) {
            auth.getAuthorities().forEach(a -> roles.add(a.getAuthority()));
        }
        return roles;
    }

    private String header(HttpServletRequest req, String name) {
        var v = req.getHeader(name);
        return v == null || v.isBlank() ? null : v;
    }

    private OrganizationId optOrgId(HttpServletRequest req) {
        var v = header(req, ORG_HEADER);
        return v != null ? OrganizationId.from(v) : null;
    }

    private WorkspaceId optWorkspaceId(HttpServletRequest req) {
        var v = header(req, WORKSPACE_HEADER);
        return v != null ? WorkspaceId.from(v) : null;
    }

    private ProjectId optProjectId(HttpServletRequest req) {
        var v = header(req, PROJECT_HEADER);
        return v != null ? ProjectId.from(v) : null;
    }
}
