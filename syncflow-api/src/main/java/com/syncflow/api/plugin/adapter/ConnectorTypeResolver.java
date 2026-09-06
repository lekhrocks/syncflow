package com.syncflow.api.plugin.adapter;

import com.syncflow.core.model.ConnectorType;

import java.util.Locale;

/**
 * Maps a free-form {@code PluginDescriptor.connectorType} string to the
 * closed {@link ConnectorType} enum. Recognized database names map to the
 * matching enum value; everything else collapses to
 * {@link ConnectorType#GENERIC_PLUGIN}.
 */
public final class ConnectorTypeResolver {

    private ConnectorTypeResolver() {
    }

    public static ConnectorType resolve(String pluginType) {
        if (pluginType == null || pluginType.isBlank()) {
            return ConnectorType.GENERIC_PLUGIN;
        }
        return switch (pluginType.toLowerCase(Locale.ROOT)) {
            case "postgresql", "postgres" -> ConnectorType.POSTGRESQL;
            case "mysql", "mariadb" -> ConnectorType.MYSQL;
            case "mongodb", "mongo" -> ConnectorType.MONGODB;
            case "kafka" -> ConnectorType.KAFKA;
            case "sqlserver", "mssql" -> ConnectorType.SQLSERVER;
            case "oracle" -> ConnectorType.ORACLE;
            case "elasticsearch", "elastic", "es" -> ConnectorType.ELASTICSEARCH;
            case "redis" -> ConnectorType.REDIS;
            case "jdbc", "generic_jdbc" -> ConnectorType.GENERIC_JDBC;
            default -> ConnectorType.GENERIC_PLUGIN;
        };
    }
}
