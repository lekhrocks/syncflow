package com.syncflow.connector;

import com.syncflow.connector.validator.MongoDbValidator;
import com.syncflow.connector.validator.MySqlValidator;
import com.syncflow.connector.validator.PostgresValidator;
import com.syncflow.connector.validator.RedisValidator;
import com.syncflow.core.connection.ConnectionProperties;
import com.syncflow.core.connection.ConnectionType;
import com.syncflow.core.connection.Credentials;
import com.syncflow.core.connection.spi.ConnectionValidator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@Tag("integration")
@EnabledIfSystemProperty(named = "tests.integration", matches = "true")
class DatabaseConnectionIntegrationTest {

    // --- PostgreSQL ---

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    static ConnectionValidator pgValidator = new PostgresValidator();

    @Test
    void postgresqlConnectionSuccess() {
        var props = new ConnectionProperties(ConnectionType.POSTGRESQL, postgres.getHost(),
                postgres.getMappedPort(5432), "testdb", Map.of());
        var creds = new Credentials("testuser", "testpass");
        var result = pgValidator.validate(props, creds);
        assertTrue(result.valid(), "PG connection should succeed: " + result.errors());
        assertNotNull(result.databaseVersion());
        assertTrue(result.latencyMs() >= 0);
    }

    @Test
    void postgresqlAuthFailure() {
        var props = new ConnectionProperties(ConnectionType.POSTGRESQL, postgres.getHost(),
                postgres.getMappedPort(5432), "testdb", Map.of());
        var creds = new Credentials("wronguser", "wrongpass");
        var result = pgValidator.validate(props, creds);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.toLowerCase().contains("auth")
                || e.toLowerCase().contains("password") || e.toLowerCase().contains("login")));
    }

    @Test
    void postgresqlConnectionTimeout() {
        var props = new ConnectionProperties(ConnectionType.POSTGRESQL, "192.0.2.1",
                5432, "testdb", Map.of("connectTimeout", "2"));
        var creds = new Credentials("u", "p");
        var result = pgValidator.validate(props, creds);
        assertFalse(result.valid());
    }

    @Test
    void postgresqlNonExistentDatabase() {
        var props = new ConnectionProperties(ConnectionType.POSTGRESQL, postgres.getHost(),
                postgres.getMappedPort(5432), "nonexistent_db", Map.of());
        var creds = new Credentials("testuser", "testpass");
        var result = pgValidator.validate(props, creds);
        assertFalse(result.valid());
    }

    // --- MySQL ---

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    static ConnectionValidator mysqlValidator = new MySqlValidator();

    @Test
    void mysqlConnectionSuccess() {
        var props = new ConnectionProperties(ConnectionType.MYSQL, mysql.getHost(),
                mysql.getMappedPort(3306), "testdb", Map.of());
        var creds = new Credentials("testuser", "testpass");
        var result = mysqlValidator.validate(props, creds);
        assertTrue(result.valid(), "MySQL connection should succeed: " + result.errors());
        assertNotNull(result.databaseVersion());
    }

    @Test
    void mysqlAuthFailure() {
        var props = new ConnectionProperties(ConnectionType.MYSQL, mysql.getHost(),
                mysql.getMappedPort(3306), "testdb", Map.of());
        var creds = new Credentials("wrong", "wrong");
        var result = mysqlValidator.validate(props, creds);
        assertFalse(result.valid());
    }

    // --- MongoDB ---

    @Container
    static GenericContainer<?> mongodb = new GenericContainer<>(DockerImageName.parse("mongo:7.0"))
            .withExposedPorts(27017)
            .waitingFor(Wait.forLogMessage(".*Waiting for connections.*", 1))
            .withStartupTimeout(Duration.ofSeconds(60));

    static ConnectionValidator mongoValidator = new MongoDbValidator();

    @Test
    void mongodbConnectionSuccess() {
        var props = new ConnectionProperties(ConnectionType.MONGODB, mongodb.getHost(),
                mongodb.getMappedPort(27017), "admin", Map.of());
        var creds = new Credentials("", "");
        var result = mongoValidator.validate(props, creds);
        assertTrue(result.valid(), "MongoDB connection should succeed: " + result.errors());
    }

    @Test
    void mongodbAuthFailure() {
        var props = new ConnectionProperties(ConnectionType.MONGODB, mongodb.getHost(),
                mongodb.getMappedPort(27017), "admin", Map.of());
        var creds = new Credentials("bad", "bad");
        var result = mongoValidator.validate(props, creds);
        assertFalse(result.valid());
    }

    // --- Redis ---

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));

    static ConnectionValidator redisValidator = new RedisValidator();

    @Test
    void redisConnectionSuccess() {
        var props = new ConnectionProperties(ConnectionType.REDIS, redis.getHost(),
                redis.getMappedPort(6379), "0", Map.of());
        var creds = new Credentials("", "");
        var result = redisValidator.validate(props, creds);
        assertTrue(result.valid(), "Redis connection should succeed: " + result.errors());
    }

    @Test
    void redisInvalidPort() {
        try {
            var props = new ConnectionProperties(ConnectionType.REDIS, redis.getHost(),
                    99999, "0", Map.of());
            var creds = new Credentials("", "");
            var result = redisValidator.validate(props, creds);
            assertFalse(result.valid());
        } catch (IllegalArgumentException e) {
            // ConnectionProperties constructor rejects out-of-range ports — this is also a
            // valid failure
            assertTrue(e.getMessage().contains("port"));
        }
    }

    // --- Connection pool exhaustion test ---
    // Validates that creating many connections doesn't leak

    @Test
    void postgresqlMultipleConnections() {
        var results = java.util.stream.IntStream.range(0, 5).parallel().mapToObj(i -> {
            var props = new ConnectionProperties(ConnectionType.POSTGRESQL, postgres.getHost(),
                    postgres.getMappedPort(5432), "testdb", Map.of());
            var creds = new Credentials("testuser", "testpass");
            return pgValidator.validate(props, creds);
        }).toList();

        assertEquals(5, results.size());
        results.forEach(r -> assertTrue(r.valid(), "All parallel connections should succeed"));
    }
}
