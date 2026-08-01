package com.syncflow.api.connection;

import com.syncflow.core.connection.Connection;
import com.syncflow.core.connection.ConnectionId;
import com.syncflow.core.connection.ConnectionMetadata;
import com.syncflow.core.connection.ConnectionProperties;
import com.syncflow.core.connection.ConnectionStatus;
import com.syncflow.core.connection.ConnectionType;
import com.syncflow.core.connection.Credentials;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionDomainTest {

    @Test
    void createConnection_generatesId() {
        var props = new ConnectionProperties(ConnectionType.POSTGRESQL, "localhost", 5432, "mydb", Map.of());
        var creds = new Credentials("user", "pass");
        var conn = new Connection("test", props, creds);

        assertNotNull(conn.getId());
        assertEquals("test", conn.getName());
        assertEquals(ConnectionStatus.CREATED, conn.getStatus());
    }

    @Test
    void markValid_updatesStatusAndMetadata() {
        var props = new ConnectionProperties(ConnectionType.POSTGRESQL, "localhost", 5432, "mydb", Map.of());
        var creds = new Credentials("user", "pass");
        var conn = new Connection("test", props, creds);

        var meta = new ConnectionMetadata("16.0", "PostgreSQL JDBC", 5, java.time.Instant.now());
        conn.markValid(meta);

        assertEquals(ConnectionStatus.VALID, conn.getStatus());
        assertEquals("16.0", conn.getMetadata().databaseVersion());
    }

    @Test
    void markInvalid_updatesStatus() {
        var props = new ConnectionProperties(ConnectionType.MYSQL, "localhost", 3306, "mydb", Map.of());
        var creds = new Credentials("user", "pass");
        var conn = new Connection("test", props, creds);

        conn.markInvalid("Connection refused");

        assertEquals(ConnectionStatus.INVALID, conn.getStatus());
    }

    @Test
    void connectionId_rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> ConnectionId.from(""));
        assertThrows(IllegalArgumentException.class, () -> ConnectionId.from(null));
    }

    @Test
    void credentials_masksPasswordInToString() {
        var creds = new Credentials("admin", "supersecret");
        var str = creds.toString();
        assertFalse(str.contains("supersecret"));
        assertTrue(str.contains("******"));
    }

    @Test
    void connectionProperties_rejectsInvalidPort() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConnectionProperties(ConnectionType.POSTGRESQL, "localhost", 0, "db", Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ConnectionProperties(ConnectionType.POSTGRESQL, "localhost", 65536, "db", Map.of()));
    }
}
