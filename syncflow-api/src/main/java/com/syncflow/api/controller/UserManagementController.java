package com.syncflow.api.controller;

import com.syncflow.api.security.rbac.AuthorizationService;
import com.syncflow.api.security.rbac.ResourcePermission;
import com.syncflow.api.user.RoleConstants;
import com.syncflow.api.user.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * User lifecycle management (create/read/update/delete/set-roles).
 * Thin transport layer — business rules live in {@link UserService}. Guarded by
 * the existing RBAC layer (admin achieves full perms via PolicyResolver).
 */
@RestController
@RequestMapping("/api/users")
public class UserManagementController {

    private final UserService service;
    private final AuthorizationService authz;

    public UserManagementController(UserService service, AuthorizationService authz) {
        this.service = service;
        this.authz = authz;
    }

    public record CreateUserRequest(
            @NotBlank String username,
            @NotBlank String password,
            String email,
            @Pattern(regexp = "[A-Za-z, ]*", message = "roles must be a comma-separated list of names") String roles) {
    }

    public record UpdateUserRequest(String email, String roles, Boolean enabled) {
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody CreateUserRequest req) {
        authz.require(ResourcePermission.ORG_WRITE);
        if (req.roles() != null && !req.roles().isBlank() && !RoleConstants.allKnown(req.roles())) {
            return ResponseEntity.badRequest().body(Map.of("error", "unknown role: " + req.roles()));
        }
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(service.toMap(service.create(req.username(), req.password(), req.email(), req.roles())));
        } catch (UserService.UserConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list() {
        authz.require(ResourcePermission.ORG_READ);
        return ResponseEntity.ok(service.list().stream().map(service::toMap).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String id) {
        authz.require(ResourcePermission.ORG_READ);
        return ResponseEntity.ok(service.toMap(service.find(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable String id,
            @Valid @RequestBody UpdateUserRequest req) {
        authz.require(ResourcePermission.ORG_WRITE);
        if (req.roles() != null && !RoleConstants.allKnown(req.roles())) {
            return ResponseEntity.badRequest().body(Map.of("error", "unknown role: " + req.roles()));
        }
        return ResponseEntity.ok(service.toMap(service.update(id, req.email(), req.roles(), req.enabled())));
    }

    @PostMapping("/{id}/roles")
    public ResponseEntity<Map<String, Object>> setRoles(@PathVariable String id,
            @RequestBody Map<String, Object> body) {
        authz.require(ResourcePermission.ORG_WRITE);
        var roles = String.valueOf(body.get("roles"));
        if (!RoleConstants.allKnown(roles)) {
            return ResponseEntity.badRequest().body(Map.of("error", "unknown role: " + roles));
        }
        return ResponseEntity.ok(service.toMap(service.setRoles(id, roles)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        authz.require(ResourcePermission.ORG_WRITE);
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
