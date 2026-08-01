package com.syncflow.api.connection.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncflow.api.connection.entity.ConnectionEntity;
import com.syncflow.core.connection.Connection;
import com.syncflow.core.connection.ConnectionId;
import com.syncflow.core.connection.ConnectionMetadata;
import com.syncflow.core.connection.ConnectionProperties;
import com.syncflow.core.connection.ConnectionStatus;
import com.syncflow.core.connection.ConnectionType;
import com.syncflow.core.connection.Credentials;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.HashMap;
import java.util.Map;

@Mapper(componentModel = "spring")
public abstract class ConnectionMapper {

    @Mapping(target = "id", expression = "java(domain.getId().value())")
    @Mapping(target = "connectionType", expression = "java(domain.getProperties().type().name())")
    @Mapping(target = "host", expression = "java(domain.getProperties().host())")
    @Mapping(target = "port", expression = "java(domain.getProperties().port())")
    @Mapping(target = "database", expression = "java(domain.getProperties().database())")
    @Mapping(target = "options", expression = "java(toJson(domain.getProperties().options()))")
    @Mapping(target = "encryptedUsername", source = "encryptedUsername")
    @Mapping(target = "encryptedPassword", source = "encryptedPassword")
    @Mapping(target = "status", expression = "java(domain.getStatus().name())")
    @Mapping(target = "dbVersion", expression = "java(domain.getMetadata().databaseVersion())")
    @Mapping(target = "driverName", expression = "java(domain.getMetadata().driverName())")
    @Mapping(target = "lastLatencyMs", expression = "java(domain.getMetadata().latencyMs())")
    @Mapping(target = "lastChecked", expression = "java(domain.getMetadata().lastChecked())")
    @Mapping(target = "createdAt", expression = "java(domain.getCreatedAt())")
    @Mapping(target = "updatedAt", expression = "java(domain.getUpdatedAt())")
    public abstract ConnectionEntity toEntity(Connection domain,
            String encryptedUsername,
            String encryptedPassword);

    public Connection toDomain(ConnectionEntity entity, Credentials credentials) {
        var id = ConnectionId.from(entity.getId());
        var type = ConnectionType.valueOf(entity.getConnectionType());
        var props = new ConnectionProperties(type, entity.getHost(), entity.getPort(),
                entity.getDatabase(), parseOptions(entity.getOptions()));
        var status = ConnectionStatus.valueOf(entity.getStatus());
        var meta = new ConnectionMetadata(
                entity.getDbVersion() != null ? entity.getDbVersion() : "unknown",
                entity.getDriverName() != null ? entity.getDriverName() : "unknown",
                entity.getLastLatencyMs(), entity.getLastChecked());
        return Connection.restore(id, entity.getName(), props, credentials, status, meta,
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    protected String toJson(Map<String, String> map) {
        if (map == null || map.isEmpty())
            return null;
        try {
            return new ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, String> parseOptions(String json) {
        if (json == null || json.isBlank())
            return Map.of();
        try {
            var mapper = new ObjectMapper();
            var type = mapper.getTypeFactory().constructMapType(
                    HashMap.class, String.class, String.class);
            return mapper.readValue(json, type);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
