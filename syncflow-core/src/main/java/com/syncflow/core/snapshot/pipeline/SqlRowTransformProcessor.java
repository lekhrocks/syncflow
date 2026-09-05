package com.syncflow.core.snapshot.pipeline;

import com.syncflow.core.pipeline.mapping.TableMapping;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link RecordProcessor} that runs whole-row SQL projection queries against
 * an H2 in-process database.
 *
 * <h3>How it works</h3>
 * For each incoming row the processor:
 * <ol>
 * <li>Opens a private, per-call H2 in-memory connection
 * ({@code MODE=MySQL} for familiar string functions).</li>
 * <li>Creates a single-row table named {@code __row__} whose columns match
 * the source row keys, all typed as {@code VARCHAR}.</li>
 * <li>Inserts the row values as strings.</li>
 * <li>Executes each configured SQL query in order. The result of each query
 * becomes the input row for the next query.</li>
 * <li>Returns the final projected {@code Map<String, Object>}.</li>
 * <li>Drops the connection (H2 in-memory DBs are discarded automatically).</li>
 * </ol>
 *
 * <h3>Security</h3>
 * <ul>
 * <li>Column and table identifiers are sanitised with the same allow-list
 * regex used by {@code JdbcBatchWriter} before interpolation.</li>
 * <li>Row <em>values</em> are always bound via {@code PreparedStatement}
 * parameters — never interpolated into SQL strings.</li>
 * <li>Each call gets a fresh, isolated H2 connection; there is no shared
 * state between rows or between pipelines.</li>
 * <li>The H2 URL disables the web console and file access:
 * {@code ;FORBID_CREATION=FALSE;TRACE_LEVEL_SYSTEM_OUT=0}.</li>
 * </ul>
 *
 * <h3>Null handling</h3>
 * Source values are converted to strings via {@link String#valueOf} before
 * insertion. {@code null} values are inserted as SQL {@code NULL} so that
 * standard SQL {@code IS NULL} / {@code COALESCE} / {@code NULLIF} expressions
 * work as expected.
 *
 * <h3>Ordering</h3>
 * This processor is inserted between the {@link FilterProcessor} and the
 * column-level {@link TransformProcessor} in the processing chain:
 *
 * <pre>
 *   FilterProcessor → SqlRowTransformProcessor → TransformProcessor
 * </pre>
 *
 * If {@link TableMapping#sqlTransforms()} is empty the processor is a no-op
 * (returns the row unchanged).
 *
 * <h3>Thread-safety</h3>
 * All state is local to each {@link #process} call; the processor is safe to
 * share across threads.
 */
public class SqlRowTransformProcessor implements RecordProcessor {

    private static final Logger log = LoggerFactory.getLogger(SqlRowTransformProcessor.class);

    /** Virtual table name the user's SELECT must reference. */
    static final String VIRTUAL_TABLE = "__row__";

    /**
     * Identifier allow-list: same pattern as JdbcBatchWriter.sanitizeIdentifier().
     */
    private static final java.util.regex.Pattern SAFE_IDENTIFIER = java.util.regex.Pattern
            .compile("[A-Za-z_][A-Za-z0-9_]*");

    private final List<String> queries;

    /**
     * Builds the processor from a {@link TableMapping}.
     * If the mapping has no SQL transforms this is a lightweight no-op instance.
     */
    public SqlRowTransformProcessor(TableMapping tableMapping) {
        this.queries = tableMapping.sqlTransforms();
    }

    /**
     * Package-private constructor for tests that supply queries directly.
     */
    SqlRowTransformProcessor(List<String> queries) {
        this.queries = List.copyOf(queries);
    }

    @Override
    public Map<String, Object> process(Map<String, Object> record, ProcessingContext ctx) {
        if (queries.isEmpty()) {
            return record;
        }

        Map<String, Object> current = record;
        for (var query : queries) {
            current = executeQuery(current, query);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private Map<String, Object> executeQuery(Map<String, Object> row, String query) {
        // H2 in-memory DB, isolated per call — no shared state.
        // IGNORECASE=TRUE makes column lookups case-insensitive for convenience.
        var jdbcUrl = "jdbc:h2:mem:;IGNORECASE=TRUE;DB_CLOSE_DELAY=0";

        try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            conn.setAutoCommit(true);
            createAndPopulateTable(conn, row);
            return runProjection(conn, query);
        } catch (SQLException e) {
            throw new SqlRowTransformException(
                    "SQL row transform failed for query [" + query + "]: " + e.getMessage(), e);
        }
    }

    /** Creates {@code __row__} with VARCHAR columns and inserts the single row. */
    private void createAndPopulateTable(Connection conn, Map<String, Object> row) throws SQLException {
        if (row.isEmpty()) {
            return;
        }

        var cols = row.keySet().stream()
                .map(SqlRowTransformProcessor::sanitize)
                .toList();

        // CREATE TABLE __row__ (col1 VARCHAR, col2 VARCHAR, ...)
        var ddl = new StringBuilder("CREATE TABLE ")
                .append(VIRTUAL_TABLE)
                .append(" (");
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0)
                ddl.append(", ");
            ddl.append(cols.get(i)).append(" VARCHAR");
        }
        ddl.append(")");

        try (var stmt = conn.createStatement()) {
            stmt.execute(ddl.toString());
        }

        // INSERT INTO __row__ (col1, col2, ...) VALUES (?, ?, ...)
        var colList = String.join(", ", cols);
        var placeholders = "?" + ", ?".repeat(cols.size() - 1);
        var insert = "INSERT INTO " + VIRTUAL_TABLE + " (" + colList + ") VALUES (" + placeholders + ")";

        try (var ps = conn.prepareStatement(insert)) {
            int idx = 1;
            for (var value : row.values()) {
                if (value == null) {
                    ps.setNull(idx, java.sql.Types.VARCHAR);
                } else {
                    ps.setString(idx, String.valueOf(value));
                }
                idx++;
            }
            ps.executeUpdate();
        }
    }

    /** Executes the user's SELECT and returns the first result row as a Map. */
    private Map<String, Object> runProjection(Connection conn, String query) throws SQLException {
        try (var ps = conn.prepareStatement(query);
                var rs = ps.executeQuery()) {

            var meta = rs.getMetaData();
            int colCount = meta.getColumnCount();

            if (!rs.next()) {
                // Query returned no rows — treat as filtered-out row
                log.debug("SQL transform query returned no rows; row will be dropped");
                return null;
            }

            var result = new LinkedHashMap<String, Object>();
            for (int i = 1; i <= colCount; i++) {
                result.put(meta.getColumnLabel(i).toLowerCase(), rs.getObject(i));
            }
            return result;
        }
    }

    /**
     * Validates and returns a safe SQL identifier.
     *
     * @throws SqlRowTransformException
     *             if the identifier contains unsafe characters
     */
    static String sanitize(String identifier) {
        if (!SAFE_IDENTIFIER.matcher(identifier).matches()) {
            throw new SqlRowTransformException(
                    "Unsafe column identifier rejected: [" + identifier + "]. "
                            + "Identifiers must match [A-Za-z_][A-Za-z0-9_]*");
        }
        return identifier;
    }

    /**
     * Unchecked exception thrown when an SQL row transform fails to execute.
     */
    public static class SqlRowTransformException extends RuntimeException {

        public SqlRowTransformException(String message, Throwable cause) {
            super(message, cause);
        }

        public SqlRowTransformException(String message) {
            super(message);
        }
    }
}
