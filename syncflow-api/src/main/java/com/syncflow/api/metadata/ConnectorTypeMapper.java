package com.syncflow.api.metadata;

import com.syncflow.core.connection.ConnectionType;
import com.syncflow.core.model.ConnectorType;

public final class ConnectorTypeMapper {

    private ConnectorTypeMapper() {
    }

    public static ConnectorType toCore(ConnectionType t) {
        return switch (t) {
            case POSTGRESQL -> ConnectorType.POSTGRESQL;
            case MYSQL -> ConnectorType.MYSQL;
            case MONGODB -> ConnectorType.MONGODB;
            case REDIS -> ConnectorType.REDIS;
        };
    }
}
