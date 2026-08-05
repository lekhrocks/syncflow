package com.syncflow.api.controller;

import com.syncflow.api.security.apikey.ApiKeyStore;
import com.syncflow.api.security.audit.EnterpriseAuditRecord;
import com.syncflow.api.security.audit.EnterpriseAuditStore;
import com.syncflow.api.security.quota.Quota;
import com.syncflow.api.security.quota.QuotaEngine;
import com.syncflow.api.security.rbac.AuthorizationService;
import com.syncflow.api.security.rbac.ResourcePermission;
import com.syncflow.tenant.OrganizationId;
import com.syncflow.tenant.ProjectId;
import com.syncflow.tenant.TenantContextHolder;
import com.syncflow.tenant.TenantId;
import com.syncflow.tenant.WorkspaceId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final QuotaEngine quotaEngine;
    private final ApiKeyStore apiKeyStore;
    private final EnterpriseAuditStore auditStore;
    private final AuthorizationService authz;

    public AdminController(QuotaEngine quotaEngine,
            ApiKeyStore apiKeyStore,
            EnterpriseAuditStore auditStore,
            AuthorizationService authz) {
        this.quotaEngine = quotaEngine;
        this.apiKeyStore = apiKeyStore;
        this.auditStore = auditStore;
        this.authz = authz;
    }

    @PostMapping("/organizations")
    public ResponseEntity<Map<String, String>> createOrg(@RequestBody Map<String, String> body) {
        authz.require(ResourcePermission.ORG_WRITE);
        var id = OrganizationId.generate();
        return ResponseEntity.ok(Map.of("id", id.value(), "name", body.getOrDefault("name", "Org")));
    }

    @PostMapping("/workspaces")
    public ResponseEntity<Map<String, String>> createWorkspace(@RequestBody Map<String, String> body) {
        authz.require(ResourcePermission.PIPELINE_WRITE);
        var id = WorkspaceId.generate();
        return ResponseEntity.ok(Map.of("id", id.value(), "name", body.getOrDefault("name", "Workspace")));
    }

    @PostMapping("/projects")
    public ResponseEntity<Map<String, String>> createProject(@RequestBody Map<String, String> body) {
        authz.require(ResourcePermission.PIPELINE_WRITE);
        var id = ProjectId.generate();
        return ResponseEntity.ok(Map.of("id", id.value(), "name", body.getOrDefault("name", "Project")));
    }

    @PostMapping("/apikeys")
    public ResponseEntity<Map<String, Object>> issueApiKey(@RequestBody Map<String, String> body) {
        authz.require(ResourcePermission.PIPELINE_EXECUTE);
        var key = apiKeyStore.issue(TenantContextHolder.getTenantId(),
                body.getOrDefault("label", "default"),
                body.getOrDefault("scope", "WRITE"),
                body.containsKey("ttlSeconds")
                        ? Instant.now().plusSeconds(Long.parseLong(body.get("ttlSeconds")))
                        : null);
        return ResponseEntity.ok(Map.of(
                "id", key.id().toString(),
                "prefix", key.prefix(),
                "expiresAt", String.valueOf(key.expiresAt())));
    }

    @DeleteMapping("/apikeys/{id}")
    public ResponseEntity<Map<String, Object>> revokeApiKey(@PathVariable UUID id) {
        authz.require(ResourcePermission.APIKEY_REVOKE);
        var ok = apiKeyStore.revoke(id);
        return ResponseEntity.ok(Map.of("revoked", ok));
    }

    @GetMapping("/quotas")
    public ResponseEntity<Quota> getQuota() {
        authz.require(ResourcePermission.ORG_READ);
        return ResponseEntity.ok(quotaEngine.getQuota(TenantContextHolder.getTenantId()));
    }

    @GetMapping("/audit")
    public ResponseEntity<List<EnterpriseAuditRecord>> listAudit(
            @RequestParam(defaultValue = "100") int limit) {
        authz.require(ResourcePermission.AUDIT_READ);
        return ResponseEntity.ok(auditStore.list(TenantContextHolder.getTenantId(), limit));
    }

    @GetMapping("/tenants")
    public ResponseEntity<Map<String, Object>> me() {
        // Self-informational: returns the caller's own tenant context; no permission
        // gate beyond being authenticated (the resource server enforces that).
        var ctx = TenantContextHolder.get();
        if (ctx == null) {
            return ResponseEntity.ok(Map.of("tenantId", TenantId.DEFAULT.value()));
        }
        // Use a mutable map so null values are allowed (Map.of() rejects nulls)
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("tenantId", ctx.tenantId().value());
        result.put("userId", ctx.userId());
        result.put("roles", ctx.roles());
        result.put("organizationId", ctx.organizationId() != null ? ctx.organizationId().value() : null);
        result.put("workspaceId", ctx.workspaceId() != null ? ctx.workspaceId().value() : null);
        result.put("projectId", ctx.projectId() != null ? ctx.projectId().value() : null);
        return ResponseEntity.ok(result);
    }
}
