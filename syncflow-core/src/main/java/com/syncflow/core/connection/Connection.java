package com.syncflow.core.connection;

import lombok.Getter;

import java.time.Instant;

@Getter
public class Connection {

    private final ConnectionId id;
    private final String name;
    private final ConnectionProperties properties;
    private final Credentials credentials;
    private ConnectionStatus status;
    private ConnectionMetadata metadata;
    private final Instant createdAt;
    private Instant updatedAt;

    public Connection(String name, ConnectionProperties properties, Credentials credentials) {
        this.id = ConnectionId.generate();
        this.name = name;
        this.properties = properties;
        this.credentials = credentials;
        this.status = ConnectionStatus.CREATED;
        this.metadata = ConnectionMetadata.unknown();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /* package-private constructor for restoring from persistence */
    Connection(ConnectionId id, String name, ConnectionProperties properties,
            Credentials credentials, ConnectionStatus status,
            ConnectionMetadata metadata, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.properties = properties;
        this.credentials = credentials;
        this.status = status;
        this.metadata = metadata;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Factory used by the persistence mapper to reconstruct a domain object. */
    public static Connection restore(ConnectionId id, String name,
            ConnectionProperties properties,
            Credentials credentials, ConnectionStatus status,
            ConnectionMetadata metadata,
            Instant createdAt, Instant updatedAt) {
        return new Connection(id, name, properties, credentials, status,
                metadata, createdAt, updatedAt);
    }

    public void markValid(ConnectionMetadata meta) {
        this.status = ConnectionStatus.VALID;
        this.metadata = meta;
        this.updatedAt = Instant.now();
    }

    public void markInvalid(String error) {
        this.status = ConnectionStatus.INVALID;
        this.metadata = new ConnectionMetadata("unknown", "unknown", 0, Instant.now());
        this.updatedAt = Instant.now();
    }

    public void markError() {
        this.status = ConnectionStatus.ERROR;
        this.updatedAt = Instant.now();
    }
}
