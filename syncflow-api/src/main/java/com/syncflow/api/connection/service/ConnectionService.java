package com.syncflow.api.connection.service;

import com.syncflow.api.connection.encryption.EncryptionService;
import com.syncflow.persistence.connection.entity.ConnectionEntity;
import com.syncflow.api.connection.mapper.ConnectionMapper;
import com.syncflow.persistence.connection.repository.ConnectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncflow.common.exception.SyncFlowException;
import com.syncflow.core.connection.Connection;
import com.syncflow.core.connection.ConnectionProperties;
import com.syncflow.core.connection.Credentials;
import com.syncflow.core.connection.spi.ConnectionLookup;
import com.syncflow.core.model.ConnectionConfiguration;
import com.syncflow.api.metadata.ConnectorTypeMapper;
import com.syncflow.tenant.TenantContext;
import com.syncflow.tenant.TenantSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Request-scoped connection service. The runtime-facing
 * {@link ConnectionLookup} port is exposed by
 * {@code ConnectionLookupAdapter}, not implemented here — one bean per port
 * keeps
 * injection unambiguous.
 */
@Service
@Transactional
public class ConnectionService {

    private final ConnectionRepository repository;
    private final ConnectionMapper mapper;
    private final EncryptionService encryption;
    private final ObjectMapper objectMapper;

    public ConnectionService(ConnectionRepository repository,
            ConnectionMapper mapper,
            EncryptionService encryption,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.mapper = mapper;
        this.encryption = encryption;
        this.objectMapper = objectMapper;
    }

    public Connection create(String name, ConnectionProperties props, Credentials credentials) {
        var connection = new Connection(name, props, credentials);
        var entity = mapper.toEntity(connection,
                encryption.encrypt(credentials.username()),
                encryption.encrypt(credentials.password()));
        entity.setTenantId(tenantId());
        repository.save(entity);
        return connection;
    }

    @Transactional(readOnly = true)
    public Connection get(String id) {
        var entity = findEntity(id);
        return toDomain(entity);
    }

    @Transactional(readOnly = true)
    public List<Connection> list() {
        return repository.findByTenantId(tenantId()).stream()
                .map(this::toDomain)
                .toList();
    }

    public Connection update(String id, String name, ConnectionProperties props, Credentials credentials) {
        var entity = findEntity(id);
        entity.setName(name);
        entity.setConnectionType(props.type().name());
        entity.setHost(props.host());
        entity.setPort(props.port());
        entity.setDatabase(props.database());
        entity.setOptions(serializeOptions(props));
        if (credentials != null) {
            entity.setEncryptedUsername(encryption.encrypt(credentials.username()));
            entity.setEncryptedPassword(encryption.encrypt(credentials.password()));
        }
        entity.setUpdatedAt(java.time.Instant.now());
        repository.save(entity);
        return toDomain(entity);
    }

    public void delete(String id) {
        var entity = findEntity(id);
        repository.delete(entity);
    }

    @Transactional(readOnly = true)
    public Connection getWithDecryptedCredentials(String id) {
        var entity = findEntity(id);
        return toDomainWithCredentials(entity);
    }

    /**
     * Tenant-explicit lookup; never depends on request ThreadLocal state.
     * Exposed to runtime code through {@code ConnectionLookupAdapter}.
     */
    @Transactional(readOnly = true)
    public ConnectionConfiguration getWithDecryptedCredentials(String id, TenantContext tenantContext) {
        TenantContext.require(tenantContext);
        var entity = repository.findByIdAndTenantId(id, tenantContext.tenantId().value())
                .orElseThrow(() -> SyncFlowException.notFound("Connection", id));
        var domain = toDomainWithCredentials(entity);
        var p = domain.getProperties();
        var c = domain.getCredentials();
        return new ConnectionConfiguration(ConnectorTypeMapper.toCore(p.type()), p.host(), p.port(),
                p.database(), c.username(), c.password(), p.options());
    }

    private ConnectionEntity findEntity(String id) {
        return repository.findByIdAndTenantId(id, tenantId())
                .orElseThrow(() -> SyncFlowException.notFound("Connection", id));
    }

    private String tenantId() {
        return TenantSupport.tenantId();
    }

    private Connection toDomain(ConnectionEntity entity) {
        return toDomain(entity, null);
    }

    private Connection toDomainWithCredentials(ConnectionEntity entity) {
        var credentials = new Credentials(
                encryption.decrypt(entity.getEncryptedUsername()),
                encryption.decrypt(entity.getEncryptedPassword()));
        return toDomain(entity, credentials);
    }

    private Connection toDomain(ConnectionEntity entity, Credentials credentials) {
        return mapper.toDomain(entity, credentials);
    }

    private String serializeOptions(ConnectionProperties props) {
        if (props.options() == null || props.options().isEmpty())
            return null;
        try {
            return objectMapper.writeValueAsString(props.options());
        } catch (Exception e) {
            return null;
        }
    }
}
