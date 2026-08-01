package com.syncflow.connector;

import com.syncflow.connector.validator.MongoDbValidator;
import com.syncflow.connector.validator.MySqlValidator;
import com.syncflow.connector.validator.PostgresValidator;
import com.syncflow.connector.validator.RedisValidator;
import com.syncflow.core.connection.ConnectionType;
import com.syncflow.core.connection.spi.ConnectorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionFactoryTest {

    private ConnectorFactory factory;

    @BeforeEach
    void setUp() {
        factory = new ConnectionValidatorRegistry(List.of(
                new PostgresValidator(),
                new MySqlValidator(),
                new MongoDbValidator(),
                new RedisValidator()));
    }

    @Test
    void postgresqlValidatorPresent() {
        var v = factory.getValidator(ConnectionType.POSTGRESQL);
        assertTrue(v.isPresent());
        assertTrue(v.get() instanceof PostgresValidator);
    }

    @Test
    void mysqlValidatorPresent() {
        var v = factory.getValidator(ConnectionType.MYSQL);
        assertTrue(v.isPresent());
        assertTrue(v.get() instanceof MySqlValidator);
    }

    @Test
    void mongodbValidatorPresent() {
        var v = factory.getValidator(ConnectionType.MONGODB);
        assertTrue(v.isPresent());
        assertTrue(v.get() instanceof MongoDbValidator);
    }

    @Test
    void redisValidatorPresent() {
        var v = factory.getValidator(ConnectionType.REDIS);
        assertTrue(v.isPresent());
        assertTrue(v.get() instanceof RedisValidator);
    }

    @Test
    void unsupportedDatabaseReturnsEmpty() {
        var v = factory.getValidator(ConnectionType.REDIS);
        assertTrue(v.isPresent()); // Redis IS supported
        // Check registry without Redis validator
        var noRedis = new ConnectionValidatorRegistry(List.of(new PostgresValidator()));
        assertTrue(noRedis.getValidator(ConnectionType.REDIS).isEmpty());
    }

    @Test
    void allValidatorsReturnsAll() {
        var all = factory.allValidators();
        assertEquals(4, all.size());
    }

    @Test
    void supportsCorrectTypes() {
        assertTrue(factory.getValidator(ConnectionType.POSTGRESQL).get().supports(ConnectionType.POSTGRESQL));
        assertFalse(factory.getValidator(ConnectionType.POSTGRESQL).get().supports(ConnectionType.MYSQL));
        assertTrue(factory.getValidator(ConnectionType.MONGODB).get().supports(ConnectionType.MONGODB));
        assertFalse(factory.getValidator(ConnectionType.MONGODB).get().supports(ConnectionType.REDIS));
    }

    @Test
    void emptyRegistryReturnsEmptyForAll() {
        var empty = new ConnectionValidatorRegistry(List.of());
        assertTrue(empty.getValidator(ConnectionType.POSTGRESQL).isEmpty());
        assertTrue(empty.getValidator(ConnectionType.MYSQL).isEmpty());
        assertTrue(empty.getValidator(ConnectionType.MONGODB).isEmpty());
        assertTrue(empty.getValidator(ConnectionType.REDIS).isEmpty());
        assertTrue(empty.allValidators().isEmpty());
    }

    @Test
    void supportsPostgresql() {
        var v = factory.getValidator(ConnectionType.POSTGRESQL).get();
        assertTrue(v.supports(ConnectionType.POSTGRESQL));
        assertFalse(v.supports(ConnectionType.MYSQL));
    }

    @Test
    void supportsMysql() {
        var v = factory.getValidator(ConnectionType.MYSQL).get();
        assertTrue(v.supports(ConnectionType.MYSQL));
        assertFalse(v.supports(ConnectionType.POSTGRESQL));
    }

    @Test
    void nullValidatorInListThrows() {
        // null validators should produce a clear error, not hang
        assertThrows(NullPointerException.class,
                () -> new ConnectionValidatorRegistry(java.util.Arrays.asList(null, null)));
    }

    @Test
    void getValidatorForUnknownType() {
        var registry = new ConnectionValidatorRegistry(List.of());
        assertTrue(registry.getValidator(ConnectionType.POSTGRESQL).isEmpty());
        assertTrue(registry.getValidator(ConnectionType.MYSQL).isEmpty());
        assertTrue(registry.getValidator(ConnectionType.MONGODB).isEmpty());
        assertTrue(registry.getValidator(ConnectionType.REDIS).isEmpty());
    }
}
