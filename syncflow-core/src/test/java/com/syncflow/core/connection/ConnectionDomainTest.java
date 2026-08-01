package com.syncflow.core.connection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionDomainTest {

    // --- ConnectionProperties validation ---

    @Test
    void createConnectionWithValidProperties() {
        var props = new ConnectionProperties(ConnectionType.POSTGRESQL, "localhost", 5432, "mydb", Map.of());
        assertEquals("localhost", props.host());
        assertEquals(5432, props.port());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void rejectBlankHost(String host) {
        assertThrows(IllegalArgumentException.class,
                () -> new ConnectionProperties(ConnectionType.POSTGRESQL, host, 5432, "db", Map.of()));
    }

    @Test
    void acceptsValidHostnames() {
        // The domain model only validates non-blank — DNS validation is connector-level
        var props = new ConnectionProperties(ConnectionType.POSTGRESQL, "!!!special_host!!!", 5432, "db", Map.of());
        assertEquals("!!!special_host!!!", props.host());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 65536, Integer.MIN_VALUE})
    void rejectInvalidPort(int port) {
        assertThrows(IllegalArgumentException.class,
                () -> new ConnectionProperties(ConnectionType.POSTGRESQL, "localhost", port, "db", Map.of()));
    }

    @Test
    void rejectNullType() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConnectionProperties(null, "localhost", 5432, "db", Map.of()));
    }

    @Test
    void rejectNullDatabase() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConnectionProperties(ConnectionType.POSTGRESQL, "h", 1, null, Map.of()));
    }

    @Test
    void rejectBlankDatabase() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConnectionProperties(ConnectionType.POSTGRESQL, "h", 1, "", Map.of()));
    }

    @Test
    void emptyOptionsMapIsValid() {
        var props = new ConnectionProperties(ConnectionType.POSTGRESQL, "h", 1, "d", Map.of());
        assertTrue(props.options().isEmpty());
    }

    @Test
    void nullOptionsDefaultsToEmpty() {
        var props = new ConnectionProperties(ConnectionType.POSTGRESQL, "h", 1, "d", null);
        assertTrue(props.options().isEmpty());
    }

    @Test
    void sslOptionRoundTrip() {
        var props = new ConnectionProperties(ConnectionType.POSTGRESQL, "h", 1, "d",
                Map.of("ssl", "true", "sslmode", "require"));
        assertEquals("true", props.options().get("ssl"));
        assertEquals("require", props.options().get("sslmode"));
    }

    @Test
    void connectionPropertiesDefensiveCopy() {
        var mutable = new java.util.HashMap<>(Map.of("ssl", "true"));
        var props = new ConnectionProperties(ConnectionType.POSTGRESQL, "h", 1, "d", mutable);
        mutable.put("ssl", "false");
        assertEquals("true", props.options().get("ssl"));
    }

    @Test
    void connectionPropertiesRejectsOverflow() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConnectionProperties(ConnectionType.POSTGRESQL, "h", 99999, "d", Map.of()));
    }

    // --- Credentials validation ---

    @Test
    void credentialsMaskPassword() {
        var c = new Credentials("admin", "supersecret");
        var s = c.toString();
        assertFalse(s.contains("supersecret"));
        assertTrue(s.contains("******"));
    }

    @Test
    void credentialsRejectNullUsername() {
        assertThrows(IllegalArgumentException.class, () -> new Credentials(null, "pass"));
    }

    @Test
    void credentialsRejectBlankUsername() {
        assertThrows(IllegalArgumentException.class, () -> new Credentials("", "pass"));
    }

    @Test
    void credentialsRejectNullPassword() {
        assertThrows(IllegalArgumentException.class, () -> new Credentials("user", null));
    }

    @Test
    void emptyPasswordIsAllowed() {
        var c = new Credentials("user", "");
        assertEquals("", c.password());
    }

    @Test
    void longPasswordAccepted() {
        var longPwd = "a".repeat(5000);
        var c = new Credentials("user", longPwd);
        assertEquals(longPwd, c.password());
    }

    // --- ConnectionMetadata ---

    @Test
    void connectionMetadataRoundTrip() {
        var meta = new ConnectionMetadata("16.0", "PG JDBC", 5, java.time.Instant.now());
        assertEquals("16.0", meta.databaseVersion());
        assertEquals("PG JDBC", meta.driverName());
        assertTrue(meta.latencyMs() >= 0);
    }

    @Test
    void connectionMetadataUnknown() {
        var meta = ConnectionMetadata.unknown();
        assertEquals("unknown", meta.databaseVersion());
        assertEquals("unknown", meta.driverName());
        assertNull(meta.lastChecked());
    }

    // --- Connection lifecycle ---

    @Test
    void connectionLifecycleStateTransitions() {
        var p = new ConnectionProperties(ConnectionType.MYSQL, "h", 3306, "d", Map.of());
        var c = new Credentials("u", "p");
        var conn = new Connection("test", p, c);
        assertEquals(ConnectionStatus.CREATED, conn.getStatus());

        var meta = new ConnectionMetadata("8.0", "MySQL Driver", 3, java.time.Instant.now());
        conn.markValid(meta);
        assertEquals(ConnectionStatus.VALID, conn.getStatus());
        assertEquals("8.0", conn.getMetadata().databaseVersion());

        conn.markInvalid("auth failure");
        assertEquals(ConnectionStatus.INVALID, conn.getStatus());

        conn.markError();
        assertEquals(ConnectionStatus.ERROR, conn.getStatus());
    }

    @Test
    void connectionNamePreservedThroughLifecycle() {
        var p = new ConnectionProperties(ConnectionType.REDIS, "r", 6379, "cache", Map.of());
        var conn = new Connection("my-redis", p, new Credentials("u", "p"));
        assertEquals("my-redis", conn.getName());
        conn.markValid(new ConnectionMetadata("7.0", "Jedis", 2, java.time.Instant.now()));
        assertEquals("my-redis", conn.getName());
    }

    // --- URL builder ---

    @Test
    void postgresUrlBuilder() {
        var props = new ConnectionProperties(ConnectionType.POSTGRESQL, "pg.example.com", 5432, "mydb",
                Map.of("sslmode", "require"));
        var url = "jdbc:postgresql://pg.example.com:5432/mydb?sslmode=require";
        assertEquals(url, buildPostgresUrl(props));
    }

    @Test
    void postgresUrlWithoutOptions() {
        var props = new ConnectionProperties(ConnectionType.POSTGRESQL, "localhost", 5432, "test", Map.of());
        assertEquals("jdbc:postgresql://localhost:5432/test", buildPostgresUrl(props));
    }

    @Test
    void mysqlUrlBuilderWithSsl() {
        var props = new ConnectionProperties(ConnectionType.MYSQL, "mysql.example.com", 3306, "inventory",
                Map.of("useSSL", "true"));
        var url = "jdbc:mysql://mysql.example.com:3306/inventory?useSSL=true";
        assertEquals(url, buildMysqlUrl(props));
    }

    @Test
    void mysqlUrlWithoutOptions() {
        var props = new ConnectionProperties(ConnectionType.MYSQL, "localhost", 3306, "test", Map.of());
        assertEquals("jdbc:mysql://localhost:3306/test", buildMysqlUrl(props));
    }

    private String buildPostgresUrl(ConnectionProperties props) {
        var url = new StringBuilder("jdbc:postgresql://");
        url.append(props.host()).append(':').append(props.port());
        url.append('/').append(props.database());
        if (!props.options().isEmpty()) {
            url.append('?');
            props.options().forEach((k, v) -> url.append(k).append('=').append(v).append('&'));
            url.setLength(url.length() - 1);
        }
        return url.toString();
    }

    private String buildMysqlUrl(ConnectionProperties props) {
        var url = new StringBuilder("jdbc:mysql://");
        url.append(props.host()).append(':').append(props.port());
        url.append('/').append(props.database());
        if (!props.options().isEmpty()) {
            url.append('?');
            props.options().forEach((k, v) -> url.append(k).append('=').append(v).append('&'));
            url.setLength(url.length() - 1);
        }
        return url.toString();
    }
}
