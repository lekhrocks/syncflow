package com.syncflow.api.connection.service;

import com.syncflow.api.connection.encryption.EncryptionService;
import com.syncflow.api.connection.entity.ConnectionEntity;
import com.syncflow.api.connection.mapper.ConnectionMapper;
import com.syncflow.api.connection.repository.ConnectionRepository;
import com.syncflow.common.exception.SyncFlowException;
import com.syncflow.core.connection.Connection;
import com.syncflow.core.connection.ConnectionProperties;
import com.syncflow.core.connection.Credentials;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ConnectionService {

    private final ConnectionRepository repository;
    private final ConnectionMapper mapper;
    private final EncryptionService encryption;

    public ConnectionService(ConnectionRepository repository,
            ConnectionMapper mapper,
            EncryptionService encryption) {
        this.repository = repository;
        this.mapper = mapper;
        this.encryption = encryption;
    }

    public Connection create(String name, ConnectionProperties props, Credentials credentials) {
        var connection = new Connection(name, props, credentials);
        var entity = mapper.toEntity(connection,
                encryption.encrypt(credentials.username()),
                encryption.encrypt(credentials.password()));
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
        return repository.findAll().stream()
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
        var credentials = new Credentials(
                encryption.decrypt(entity.getEncryptedUsername()),
                encryption.decrypt(entity.getEncryptedPassword()));
        return toDomain(entity, credentials);
    }

    private ConnectionEntity findEntity(String id) {
        return repository.findById(id)
                .orElseThrow(() -> SyncFlowException.notFound("Connection", id));
    }

    private Connection toDomain(ConnectionEntity entity) {
        return toDomain(entity, null);
    }

    private Connection toDomain(ConnectionEntity entity, Credentials credentials) {
        return mapper.toDomain(entity, credentials);
    }

    private String serializeOptions(ConnectionProperties props) {
        if (props.options() == null || props.options().isEmpty())
            return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(props.options());
        } catch (Exception e) {
            return null;
        }
    }
}
