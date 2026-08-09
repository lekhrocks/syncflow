package com.syncflow.api.config;

import com.syncflow.tenant.TenantId;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * JWT -> authentication converter that carries the caller's tenant scope
 * (tenant/org/workspace/project) in the token details. TenantFilter reads the
 * tenant from this principal — NEVER from client headers — so an authenticated
 * caller cannot switch tenant by spoofing {@code X-Tenant-Id}.
 *
 * The tenant claim is read as {@code tid} (or {@code tenant}). Org/workspace/
 * project are best-effort from {@code oid}/{@code wid}/{@code pid} (or their
 * {@code *Id} spellings). A legacy subject of the form
 * {@code tenant:{tenant}:{org}:{workspace}:{project}} is also honored.
 *
 * Authority mapping mirrors the default {@code JwtAuthenticationConverter}: the
 * {@code scope} claim becomes {@code SCOPE_*}-prefixed authorities (RBAC's
 * {@code PolicyResolver} still grants ADMIN via the username or the ADMIN role).
 */
@Component
public class TenantJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = authoritiesConverter.convert(jwt);
        var token = new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        token.setDetails(extractTenant(jwt));
        return token;
    }

    /** Tenant scope carried on the token details; read by {@code TenantFilter}. */
    public record PrincipalTenant(TenantId tenantId, String organizationId,
                                  String workspaceId, String projectId) {
    }

    static PrincipalTenant extractTenant(Jwt jwt) {
        var tenantClaim = jwt.hasClaim("tid") ? jwt.getClaimAsString("tid")
                : jwt.hasClaim("tenant") ? jwt.getClaimAsString("tenant") : null;
        var subject = jwt.getSubject();

        if (tenantClaim == null && subject != null && subject.startsWith("tenant:")) {
            var parts = subject.substring("tenant:".length()).split(":", -1);
            if (parts.length >= 4) {
                return new PrincipalTenant(
                        TenantId.from(parts[0]),
                        blankToNull(parts[1]),
                        blankToNull(parts[2]),
                        blankToNull(parts[3]));
            }
        }
        if (tenantClaim != null) {
            return new PrincipalTenant(
                    TenantId.from(tenantClaim),
                    claimString(jwt, "oid", "organizationId"),
                    claimString(jwt, "wid", "workspaceId"),
                    claimString(jwt, "pid", "projectId"));
        }
        return new PrincipalTenant(TenantId.DEFAULT, null, null, null);
    }

    private static String claimString(Jwt jwt, String primary, String secondary) {
        var v = jwt.hasClaim(primary) ? jwt.getClaimAsString(primary) : null;
        return v != null ? v : (jwt.hasClaim(secondary) ? jwt.getClaimAsString(secondary) : null);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
