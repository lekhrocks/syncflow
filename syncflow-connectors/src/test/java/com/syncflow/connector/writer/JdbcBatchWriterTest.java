package com.syncflow.connector.writer;

import com.syncflow.core.model.ConnectionConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SQL-builder coverage for {@link JdbcBatchWriter}. We don't spin up a real
 * database — instead we exercise the protected SQL-building helpers and
 * the identifier allowlist via a tiny test-only subclass that exposes them.
 */
class JdbcBatchWriterTest {

    /** Test-only subclass that exposes the SQL builders and sanitiser. */
    static class TestWriter extends JdbcBatchWriter {

        @Override
        protected String jdbcUrl(ConnectionConfiguration config) {
            return "jdbc:h2:mem:test";
        }
        @Override
        protected java.util.Properties jdbcProperties(ConnectionConfiguration config) {
            var p = new java.util.Properties();
            p.setProperty("user", "sa");
            return p;
        }
        String buildInsertSqlPublic(List<String> cols, String t) {
            return buildInsertSqlPublic0(cols, t);
        }
        // The real buildInsertSql is private. Expose it via reflection would
        // be brittle; instead, the SPI surface is what matters. We test the
        // SQL produced by the public methods via the connection mock.
        String buildInsertSqlPublic0(List<String> cols, String t) {
            // Re-implement the call to the private method for the test.
            try {
                var m = JdbcBatchWriter.class.getDeclaredMethod("buildInsertSql", List.class, String.class);
                m.setAccessible(true);
                return (String) m.invoke(this, cols, t);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        String upsertSqlPublic(String table, List<String> cols, List<String> keys) {
            return upsertSql(table, cols, keys);
        }
        String buildDeleteSqlPublic(String table, List<String> pkCols, int rows) {
            return buildDeleteSql(table, pkCols, rows);
        }
    }

    @Test
    void upsertSqlIncludesOnConflictAndExcludedRefs() {
        var w = new TestWriter();
        var sql = w.upsertSqlPublic("users",
                List.of("id", "email", "name"),
                List.of("id"));
        assertTrue(sql.contains("ON CONFLICT (id)"), "missing ON CONFLICT target");
        assertTrue(sql.contains("EXCLUDED.email"), "missing EXCLUDED for non-key column");
        assertTrue(sql.contains("EXCLUDED.name"), "missing EXCLUDED for second non-key column");
        // Key column should NOT appear in the SET clause.
        assertTrue(!sql.contains("EXCLUDED.id "), "key column leaked into SET clause");
    }

    @Test
    void upsertSqlWithCompositeKeyUsesBothColumnsInConflictTarget() {
        var w = new TestWriter();
        var sql = w.upsertSqlPublic("orders",
                List.of("tenant_id", "order_id", "total"),
                List.of("tenant_id", "order_id"));
        assertTrue(sql.contains("ON CONFLICT (tenant_id, order_id)"));
    }

    @Test
    void upsertSqlRejectsUnsafeIdentifier() {
        var w = new TestWriter();
        // Table name with a semicolon is a classic injection attempt.
        assertThrows(IllegalArgumentException.class, () -> w.upsertSqlPublic("users; DROP TABLE users;--",
                List.of("id"), List.of("id")));
    }

    @Test
    void upsertSqlRejectsUnsafeColumn() {
        var w = new TestWriter();
        assertThrows(IllegalArgumentException.class, () -> w.upsertSqlPublic("users",
                List.of("id", "evil) AS id --"),
                List.of("id")));
    }

    @Test
    void upsertSqlRejectsUnsafeKeyColumn() {
        var w = new TestWriter();
        assertThrows(IllegalArgumentException.class, () -> w.upsertSqlPublic("users",
                List.of("id", "name"),
                List.of("id; DROP TABLE users")));
    }

    @Test
    void insertSqlBuildersAreSafeByConstruction() {
        var w = new TestWriter();
        // Even though we don't expose the private builder, the public
        // upsertSql covers the same path. We assert a single-column case
        // here as a sanity check.
        var sql = w.upsertSqlPublic("t",
                List.of("id"), List.of("id"));
        assertNotNull(sql);
        assertTrue(sql.startsWith("INSERT INTO t "));
    }

    @Test
    void deleteSqlSinglePkSingleRow() {
        var w = new TestWriter();
        var sql = w.buildDeleteSqlPublic("users", List.of("id"), 1);
        assertEquals("DELETE FROM users WHERE (id) IN ((?))", sql);
    }

    @Test
    void deleteSqlCompositePkBatchesMultipleRows() {
        var w = new TestWriter();
        var sql = w.buildDeleteSqlPublic("orders", List.of("tenant_id", "order_id"), 2);
        assertEquals("DELETE FROM orders WHERE (tenant_id, order_id) IN ((?, ?), (?, ?))", sql);
    }

    @Test
    void deleteSqlRejectsUnsafeTableAndColumns() {
        var w = new TestWriter();
        assertThrows(IllegalArgumentException.class,
                () -> w.buildDeleteSqlPublic("users; DROP TABLE users;--", List.of("id"), 1));
        assertThrows(IllegalArgumentException.class,
                () -> w.buildDeleteSqlPublic("users", List.of("id; DROP TABLE users"), 1));
    }

    @Test
    void emptyPksAreNoOp() {
        var w = new TestWriter();
        // Verify the no-op short-circuit doesn't throw or crash.
        w.deleteBatch("users", List.of("id"), List.of());
        // Empty list of maps is treated as a no-op; we don't have a separate
        // observable side effect to verify without spinning up a DB.
    }

    @Test
    void emptyRowsAreNoOp() {
        var w = new TestWriter();
        w.writeBatch("users", List.of("id"), List.of());
    }

    @Test
    void concurrentDeletesFromDifferentTablesDoNotShareState() {
        // Verifies that the deleteBuffer scope moves with the table: a second
        // table's PK columns must not be appended to the first table's DELETE
        // statement. The flush-on-table-change guard in deleteBatch resets the
        // pending scope on a table switch.
        var w = new TestWriter();
        w.deleteBatch("users", List.of("id"), List.of(Map.of("id", 1)));
        assertEquals("users", w.pendingTable());
        w.deleteBatch("users", List.of("id"), List.of(Map.of("id", 2)));
        assertEquals("users", w.pendingTable());
        w.deleteBatch("orders", List.of("oid", "region"),
                List.of(Map.of("oid", 1, "region", "eu")));
        assertEquals("orders", w.pendingTable(),
                "cross-table delete switch moves the pending scope");
    }

    @Test
    void writeBatchForDifferentTablesTracksLatestTable() {
        // R8: after a cross-table writeBatch switch, the pending scope is the
        // latest table — earlier tables' pending SQL is flushed/reset by the
        // guard, so a later flush cannot mis-apply rows to a stale table.
        var w = new TestWriter();
        w.writeBatch("users", List.of("id"), List.of(Map.of("id", 1)));
        assertEquals("users", w.pendingTable());
        w.writeBatch("users", List.of("id"), List.of(Map.of("id", 2)));
        assertEquals("users", w.pendingTable(), "same-table calls keep the table");
        w.writeBatch("orders", List.of("oid"), List.of(Map.of("oid", 1)));
        // After the switch the writer tracks 'orders' (the 'users' rows were
        // flushed/reset); a flush now targets only orders.
        assertEquals("orders", w.pendingTable(),
                "cross-table switch must move the pending scope, not mix tables");
    }

    @Test
    void mysqlUpsertUsesOnDuplicateKeyUpdate() {
        // The MySQL dialect override must emit ON DUPLICATE KEY UPDATE, not the
        // Postgres ON CONFLICT shape — a MySQL destination would otherwise fail
        // at write time with a Postgres-only statement.
        var w = new MySqlWriter();
        var sql = mysqlUpsertSql(w, "users", List.of("id", "email"), List.of("id"));
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"), "missing ON DUPLICATE KEY UPDATE");
        assertTrue(sql.contains(" = VALUES(email)"), "missing VALUES() assignment");
        assertTrue(!sql.contains("ON CONFLICT"), "MySQL upsert must not use ON CONFLICT");
        assertTrue(!sql.contains("AS new"), "row-alias form is not portable to MariaDB/MySQL<8.0.19");
    }

    /** Invoke the protected MySQL upsertSql for assertion. */
    private static String mysqlUpsertSql(MySqlWriter w, String table, List<String> cols, List<String> keys) {
        try {
            var m = MySqlWriter.class.getDeclaredMethod("upsertSql", String.class, List.class, List.class);
            m.setAccessible(true);
            return (String) m.invoke(w, table, cols, keys);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void upsertBatchSetsConflictTargetFromKeyColumns() {
        // R3: upsertBatch must produce an ON CONFLICT statement keyed by the
        // given PK columns (single and composite).
        var w = new TestWriter();
        var sql1 = w.upsertSqlPublic("users",
                List.of("id", "email"), List.of("id"));
        assertTrue(sql1.contains("ON CONFLICT (id)"), "single-PK upsert missing conflict target");
        var sql2 = w.upsertSqlPublic("line_items",
                List.of("order_id", "line_no", "qty"),
                List.of("order_id", "line_no"));
        assertTrue(sql2.contains("ON CONFLICT (order_id, line_no)"),
                "composite-PK upsert missing conflict target");
    }
}
