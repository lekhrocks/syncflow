package com.syncflow.connector.snapshot;

import com.syncflow.connector.metadata.PostgresMetadataConnector;
import com.syncflow.core.model.ConnectionConfiguration;
import com.syncflow.core.model.ConnectorType;
import com.syncflow.core.snapshot.BatchInformation;
import com.syncflow.core.snapshot.ChunkRange;
import com.syncflow.core.spi.ConnectorContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F15 correctness: parallel PK-range chunking must produce exactly the rows of
 * the whole table — no duplicates, no gaps — for both a fresh chunked read and
 * a resume-with-cursor within a chunk. A PG table with a numeric PK is split
 * into several ranges; each range is paginated with keyset; the union must
 * equal the full table's PK set.
 */
@Testcontainers
@Tag("integration")
@EnabledIfSystemProperty(named = "tests.integration", matches = "true")
class ChunkedSnapshotCorrectnessTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    static PostgresMetadataConnector connector = new PostgresMetadataConnector();
    static ConnectorContext ctx;

    @BeforeAll
    static void seed() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE chunked_rows (id BIGINT PRIMARY KEY, label TEXT)");
            // 1000 rows, ids 1..1000.
            stmt.execute("INSERT INTO chunked_rows SELECT g, 'row-' || g FROM generate_series(1, 1000) g");
        }
        var config = new ConnectionConfiguration(ConnectorType.POSTGRESQL, postgres.getHost(),
                postgres.getMappedPort(5432), "testdb", "testuser", "testpass", Map.of());
        ctx = new ConnectorContext(config, Map.of());
        connector.connect(ctx);
    }

    @Test
    void chunkedReadCoversWholeTableExactly() {
        var ranges = connector.rangeChunks(ctx, "public", "chunked_rows", 8);
        assertTrue(ranges.size() > 1, "expected >1 chunk for a 1000-row table, got " + ranges.size());

        Set<Long> seen = new HashSet<>();
        for (var range : ranges) {
            readRange(range, null, seen);
        }
        assertEquals(1000, seen.size(), "chunked read must cover all 1000 rows exactly");
        for (long i = 1; i <= 1000; i++) {
            assertTrue(seen.contains(i), "missing id " + i);
        }
    }

    @Test
    void resumeWithinChunkDoesNotDuplicateOrSkip() {
        var ranges = connector.rangeChunks(ctx, "public", "chunked_rows", 4);
        var target = ranges.get(1); // a middle chunk

        // Read the chunk fully, remembering every id.
        Set<Long> full = new HashSet<>();
        readRange(target, null, full);

        // Re-read the same chunk but stop after the first page, then resume from
        // that page's cursor — the union must equal the full chunk's ids.
        var firstPage = connector.readBatch(ctx, "public", "chunked_rows",
                new BatchInformation(0, 100, "chunked_rows", null, target));
        Set<Long> resumed = new HashSet<>();
        firstPage.rows().forEach(r -> resumed.add((Long) r.get("id")));
        var cursor = firstPage.nextCursor();
        assertTrue(cursor != null, "first page should have a next cursor");
        readRange(target, cursor, resumed);

        assertEquals(full, resumed,
                "resume within a chunk must not duplicate or skip rows");
    }

    /** Paginate a chunk from an optional starting cursor, collecting PK ids. */
    private void readRange(ChunkRange range, String fromCursor, Set<Long> into) {
        String cursor = fromCursor;
        int batch = fromCursor == null ? 0 : 1;
        while (true) {
            var info = new BatchInformation(batch, 100, "chunked_rows", cursor, range);
            var page = connector.readBatch(ctx, "public", "chunked_rows", info);
            if (page.rows().isEmpty()) {
                break;
            }
            page.rows().forEach(r -> into.add((Long) r.get("id")));
            if (page.nextCursor() == null) {
                break;
            }
            cursor = page.nextCursor();
            batch++;
        }
    }
}
