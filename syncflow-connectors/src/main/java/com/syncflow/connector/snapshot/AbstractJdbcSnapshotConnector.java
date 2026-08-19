package com.syncflow.connector.snapshot;

import com.syncflow.connector.metadata.AbstractJdbcMetadataConnector;
import com.syncflow.core.snapshot.BatchInformation;
import com.syncflow.core.snapshot.ChunkRange;
import com.syncflow.core.spi.ConnectorContext;
import com.syncflow.core.spi.SnapshotCapableConnector;

import java.math.BigDecimal;
import java.math.MathContext;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractJdbcSnapshotConnector
        extends
            AbstractJdbcMetadataConnector
        implements
            SnapshotCapableConnector {

    @Override
    public long estimateRows(ConnectorContext ctx, String schema, String table) {
        ensureConnected(ctx);
        // Prefer the planner's reltuples estimate (near-instant, no full scan)
        // over SELECT COUNT(*), which reads the whole table just for progress %.
        var est = estimateFromCatalog(schema, table);
        if (est > 0) {
            return est;
        }
        // Fallback: exact count only when no planner estimate is available.
        var sql = "SELECT COUNT(*) FROM " + schema + "." + table;
        try (var stmt = jdbcConnection.createStatement();
                var rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    /** Postgres planner estimate (reltuples) — cheap, avoids a full table scan. */
    private long estimateFromCatalog(String schema, String table) {
        var sql = "SELECT c.reltuples::bigint FROM pg_class c "
                + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                + "WHERE n.nspname = ? AND c.relname = ?";
        try (var stmt = jdbcConnection.prepareStatement(sql)) {
            stmt.setString(1, schema);
            stmt.setString(2, table);
            var rs = stmt.executeQuery();
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    @Override
    public Page readBatch(ConnectorContext ctx, String schema, String table,
            BatchInformation batchInfo) {
        ensureConnected(ctx);
        var pkCol = primaryKeyColumn(ctx, schema, table);

        // Keyset (seek) pagination when a single-column PK is available: stable
        // under concurrent writes (no OFFSET drift / duplication) and single-pass.
        if (pkCol != null) {
            return readKeysetPage(ctx, schema, table, batchInfo, pkCol);
        }
        // Fallback: OFFSET/LIMIT for tables without a single-column PK. Not
        // snapshot-isolated, but the cursor is still carried so the executor can
        // resume from the batch.
        return readOffsetPage(schema, table, batchInfo);
    }

    /**
     * A disjoint, already-connected clone for a parallel snapshot worker. Each
     * worker gets its own {@code java.sql.Connection} via the concrete
     * connector's no-arg constructor, so a single Connection is never shared
     * across threads (JDBC Connection is not thread-safe).
     */
    @Override
    public SnapshotCapableConnector snapshotClone(ConnectorContext ctx) {
        try {
            var clone = getClass().getDeclaredConstructor().newInstance();
            clone.connect(ctx);
            return clone;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Snapshot connector " + getClass().getSimpleName() + " needs a no-arg constructor to clone", e);
        }
    }

    /**
     * Split a table into PK-range chunks (F15). Returns the whole table as one
     * chunk when there is no single-column PK, or when the PK is not numeric
     * (uuid/text/date ranges cannot be split arithmetically). Chunks are
     * disjoint and gapless: {@code [min, max]} split evenly for a numeric PK.
     */
    @Override
    public List<ChunkRange> rangeChunks(ConnectorContext ctx, String schema, String table,
            int chunkCount) {
        ensureConnected(ctx);
        var pkCol = primaryKeyColumn(ctx, schema, table);
        if (pkCol == null) {
            return List.of(ChunkRange.whole());
        }
        var minMax = minMaxPk(schema, table, pkCol);
        if (minMax == null || minMax[0] == null || minMax[1] == null
                || !(minMax[0] instanceof Number left) || !(minMax[1] instanceof Number right)) {
            // Non-numeric PK (uuid / text / date) — cannot split by value ranges.
            return List.of(ChunkRange.whole());
        }
        // Numeric PKs split into disjoint [start, end) value ranges. Integer PKs
        // (BIGINT/INT) split arithmetically; a decimal (NUMERIC) PK splits on
        // exact BigDecimal so fractional values are not truncated and dropped.
        if (left instanceof BigDecimal) {
            return decimalRanges((BigDecimal) left, (BigDecimal) right, chunkCount);
        }
        long min = left.longValue();
        long max = right.longValue();
        int chunks = Math.max(1, chunkCount);
        var list = new ArrayList<ChunkRange>(chunks);
        // Split the [min, max] domain arithmetically in BigDecimal so neither
        // `max - min + 1` nor `max + 1` can overflow a signed long (a BIGINT PK
        // may span Long.MIN_VALUE..Long.MAX_VALUE). Bounds are emitted back as
        // native Longs so the driver binds them to the bigint column. A chunk
        // whose boundary passes max is the effective last chunk and carries a
        // null end (open-ended) — this also sidesteps end == max + 1 overflow.
        var hi = BigDecimal.valueOf(max);
        var span = hi.subtract(BigDecimal.valueOf(min)).add(BigDecimal.ONE);
        var step = span.divide(BigDecimal.valueOf(chunks), MathContext.DECIMAL128)
                .setScale(0, java.math.RoundingMode.CEILING)
                .max(BigDecimal.ONE);
        var start = BigDecimal.valueOf(min);
        for (int i = 0; i < chunks; i++) {
            var boundary = start.add(step);
            Long end;
            if (boundary.compareTo(hi) > 0) {
                // Remaining domain [start, max] fits in this last chunk — open end.
                end = null;
            } else {
                end = boundary.longValueExact();
            }
            list.add(new ChunkRange(i, start.longValueExact(), end));
            if (end == null) {
                break;
            }
            start = boundary;
        }
        return list;
    }

    /** Split a BigDecimal PK range into disjoint [start, end) chunks exactly. */
    private static List<ChunkRange> decimalRanges(BigDecimal min, BigDecimal max, int chunkCount) {
        int chunks = Math.max(1, chunkCount);
        var span = max.subtract(min);
        var step = span.divide(BigDecimal.valueOf(chunks), MathContext.DECIMAL128)
                .max(BigDecimal.ONE);
        var list = new ArrayList<ChunkRange>(chunks);
        var start = min;
        for (int i = 0; i < chunks; i++) {
            var end = i == chunks - 1 ? max.add(BigDecimal.ONE) : start.add(step);
            list.add(new ChunkRange(i, start, end));
            if (end.compareTo(max) > 0) {
                break;
            }
            start = end;
        }
        return list;
    }

    /**
     * Read a page within a chunk range: {@code WHERE pk >= start AND pk < end
      * (plus > cursor on resume) ORDER BY pk LIMIT size}.
     */
    private Page readKeysetPage(ConnectorContext ctx, String schema, String table,
            BatchInformation batchInfo, String pkCol) {
        var cursor = batchInfo.cursor();
        var chunk = batchInfo.chunkRange();
        var clause = new StringBuilder(" WHERE " + pkCol);
        if (cursor != null) {
            clause.append(" > ?");
        } else if (chunk != null && chunk.start() != null) {
            clause.append(" >= ?");
        } else {
            clause.append(" IS NOT NULL");
        }
        if (chunk != null && chunk.end() != null) {
            clause.append(" AND " + pkCol + " < ?");
        }
        String sql = "SELECT * FROM " + schema + "." + table + clause
                + " ORDER BY " + pkCol + " LIMIT " + batchInfo.batchSize();
        var rows = new ArrayList<Map<String, Object>>();
        Object lastPk = null;
        try (var stmt = jdbcConnection.prepareStatement(sql)) {
            int param = 1;
            if (cursor != null) {
                // The cursor round-trips through a String (SPI contract). For a
                // numeric PK the range bounds are native Numbers, so coerce the
                // cursor to Long to match — a raw String fails the type check
                // ("operator does not exist: bigint >= character varying").
                stmt.setObject(param++, coerceCursor(cursor, chunk));
            } else if (chunk != null && chunk.start() != null) {
                stmt.setObject(param++, chunk.start());
            }
            if (chunk != null && chunk.end() != null) {
                stmt.setObject(param++, chunk.end());
            }
            var rs = stmt.executeQuery();
            var meta = rs.getMetaData();
            int pkIndex = columnIndex(meta, pkCol);
            int cols = meta.getColumnCount();
            while (rs.next()) {
                var row = new LinkedHashMap<String, Object>();
                for (int i = 1; i <= cols; i++) {
                    row.put(meta.getColumnName(i), rs.getObject(i));
                }
                rows.add(row);
                if (pkIndex > 0) {
                    lastPk = rs.getObject(pkIndex);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Keyset batch read failed for " + schema + "." + table, e);
        }
        var nextCursor = !rows.isEmpty() && rows.size() == batchInfo.batchSize()
                ? String.valueOf(lastPk)
                : null;
        return rows.isEmpty() ? Page.empty() : Page.of(rows, nextCursor);
    }

    /**
     * Bind the keyset cursor as the PK's native type. The SPI cursor is a
     * String; for numeric PKs the driver rejects a bare String against a
     * bigint column ("operator does not exist: bigint >= character varying"),
     * so parse a numeric-looking cursor to {@link Long}. Non-numeric PKs
     * (uuid/text) fall through to the raw String, which the driver handles.
     */
    private static Object coerceCursor(String cursor, ChunkRange chunk) {
        if (cursor == null || cursor.isEmpty()) {
            return cursor;
        }
        // Bind the cursor in the same numeric type as the chunk bounds so the
        // comparison operator matches the PK column type.
        if (chunk != null && chunk.start() instanceof BigDecimal) {
            return new BigDecimal(cursor);
        }
        boolean numericRange = chunk != null && chunk.start() instanceof Number;
        if (numericRange || isNumericCursor(cursor)) {
            try {
                return Long.valueOf(cursor);
            } catch (NumberFormatException e) {
                return cursor;
            }
        }
        return cursor;
    }

    /** True when the cursor parses as a Long (numeric PK value). */
    private static boolean isNumericCursor(String cursor) {
        if (cursor == null || cursor.isEmpty()) {
            return false;
        }
        for (int i = 0; i < cursor.length(); i++) {
            if (i == 0 && (cursor.charAt(0) == '-' || cursor.charAt(0) == '+')) {
                continue;
            }
            if (!Character.isDigit(cursor.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** MIN/MAX of the PK column as bound driver values, or null on failure. */
    private Object[] minMaxPk(String schema, String table, String pkCol) {
        var sql = "SELECT MIN(" + pkCol + "), MAX(" + pkCol + ") FROM " + schema + "." + table;
        try (var stmt = jdbcConnection.createStatement();
                var rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return new Object[]{rs.getObject(1), rs.getObject(2)};
            }
        } catch (SQLException e) {
            return null;
        }
        return null;
    }

    /** OFFSET/LIMIT fallback for tables without a single-column PK. */
    private Page readOffsetPage(String schema, String table, BatchInformation batchInfo) {
        int offset = batchInfo.cursor() != null
                ? Integer.parseInt(batchInfo.cursor())
                : batchInfo.batchNumber() * batchInfo.batchSize();
        var sql = "SELECT * FROM " + schema + "." + table
                + " OFFSET " + offset + " LIMIT " + batchInfo.batchSize();
        var rows = new ArrayList<Map<String, Object>>();
        try (var stmt = jdbcConnection.createStatement();
                var rs = stmt.executeQuery(sql)) {
            var meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            while (rs.next()) {
                var row = new LinkedHashMap<String, Object>();
                for (int i = 1; i <= cols; i++) {
                    row.put(meta.getColumnName(i), rs.getObject(i));
                }
                rows.add(row);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Batch read failed for " + schema + "." + table, e);
        }
        var nextOffset = offset + rows.size();
        var nextCursor = rows.size() == batchInfo.batchSize()
                ? String.valueOf(nextOffset)
                : null;
        return rows.isEmpty() ? Page.empty() : Page.of(rows, nextCursor);
    }

    /** Single-column primary key if the table has one, else null. */
    private String primaryKeyColumn(ConnectorContext ctx, String schema, String table) {
        var pk = fetchPrimaryKey(ctx, schema, table);
        return (pk != null && pk.columnNames().size() == 1)
                ? sanitizeIdentifier(pk.columnNames().get(0))
                : null;
    }

    /**
     * Only allow identifiers safe to interpolate into SQL. DB metadata is usually
     * trusted, but a crafted column name must not become an injection vector.
     */
    private static String sanitizeIdentifier(String name) {
        if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Unsafe column identifier: " + name);
        }
        return name;
    }

    private static int columnIndex(ResultSetMetaData meta, String name) throws SQLException {
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            if (meta.getColumnName(i).equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }
}
