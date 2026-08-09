package com.syncflow.api.controller;

import com.syncflow.api.connection.dto.ConnectionHealthResponse;
import com.syncflow.api.security.rbac.AuthorizationService;
import com.syncflow.api.security.rbac.ResourcePermission;
import com.syncflow.api.connection.dto.ConnectionResponse;
import com.syncflow.api.connection.dto.CreateConnectionRequest;
import com.syncflow.api.connection.dto.TestConnectionRequest;
import com.syncflow.api.connection.dto.TestConnectionResponse;
import com.syncflow.api.connection.dto.UpdateConnectionRequest;
import com.syncflow.api.connection.service.ConnectionService;
import com.syncflow.core.connection.ConnectionProperties;
import com.syncflow.core.connection.Credentials;
import com.syncflow.core.connection.spi.ConnectorFactory;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/connections")
public class ConnectionController {

    private final ConnectionService connectionService;
    private final ConnectorFactory connectorFactory;
    private final AuthorizationService authz;

    public ConnectionController(ConnectionService connectionService,
            ConnectorFactory connectorFactory,
            AuthorizationService authz) {
        this.connectionService = connectionService;
        this.connectorFactory = connectorFactory;
        this.authz = authz;
    }

    @PostMapping
    public ResponseEntity<ConnectionResponse> create(@Valid @RequestBody CreateConnectionRequest req) {
        authz.require(ResourcePermission.CONNECTION_WRITE);
        var props = new ConnectionProperties(req.connectionType(), req.host(), req.port(),
                req.database(), req.options() != null ? req.options() : Map.of());
        var credentials = new Credentials(
                req.username() != null ? req.username() : "",
                req.password() != null ? req.password() : "");
        var connection = connectionService.create(req.name(), props, credentials);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ConnectionResponse.from(connection, true));
    }

    @GetMapping
    public ResponseEntity<List<ConnectionResponse>> list() {
        authz.require(ResourcePermission.CONNECTION_READ);
        var list = connectionService.list().stream()
                .map(c -> ConnectionResponse.from(c, true))
                .toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConnectionResponse> get(@PathVariable String id) {
        authz.require(ResourcePermission.CONNECTION_READ);
        return ResponseEntity.ok(ConnectionResponse.from(connectionService.get(id), true));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ConnectionResponse> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateConnectionRequest req) {
        authz.require(ResourcePermission.CONNECTION_WRITE);
        var existing = connectionService.get(id);
        var props = new ConnectionProperties(existing.getProperties().type(),
                req.host(), req.port(), req.database(),
                req.options() != null ? req.options() : Map.of());
        Credentials credentials = null;
        if (req.password() != null && !req.password().isBlank()) {
            credentials = new Credentials(
                    req.username() != null ? req.username() : "",
                    req.password());
        }
        var connection = connectionService.update(id, req.name(), props, credentials);
        return ResponseEntity.ok(ConnectionResponse.from(connection, true));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        authz.require(ResourcePermission.CONNECTION_DELETE);
        connectionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test")
    public ResponseEntity<TestConnectionResponse> test(@Valid @RequestBody TestConnectionRequest req) {
        authz.require(ResourcePermission.CONNECTION_WRITE);
        var props = new ConnectionProperties(req.connectionType(), req.host(), req.port(),
                req.database(), req.options() != null ? req.options() : Map.of());
        var credentials = new Credentials(
                req.username() != null ? req.username() : "",
                req.password() != null ? req.password() : "");

        var validator = connectorFactory.getValidator(props.type());
        if (validator.isEmpty()) {
            return ResponseEntity.ok(new TestConnectionResponse(false, 0, null, null,
                    "Unsupported connection type: " + props.type()));
        }

        var result = validator.get().validate(props, credentials);
        return ResponseEntity.ok(new TestConnectionResponse(
                result.valid(),
                result.latencyMs(),
                result.databaseVersion(),
                result.driverName(),
                result.valid() ? null : String.join("; ", result.errors())));
    }

    @GetMapping("/{id}/health")
    public ResponseEntity<ConnectionHealthResponse> health(@PathVariable String id) {
        authz.require(ResourcePermission.CONNECTION_READ);
        var connection = connectionService.getWithDecryptedCredentials(id);
        var validator = connectorFactory.getValidator(connection.getProperties().type());
        if (validator.isEmpty()) {
            return ResponseEntity.ok(new ConnectionHealthResponse("UNSUPPORTED", 0, null, null));
        }

        var result = validator.get().validate(connection.getProperties(), connection.getCredentials());
        var status = result.valid() ? "ONLINE" : "OFFLINE";
        return ResponseEntity.ok(new ConnectionHealthResponse(
                status, result.latencyMs(), result.databaseVersion(), java.time.Instant.now()));
    }
}
