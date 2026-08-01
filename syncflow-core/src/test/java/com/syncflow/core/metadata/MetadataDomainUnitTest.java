package com.syncflow.core.metadata;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadataDomainUnitTest {

    // --- DataType ---

    @Test
    void dataTypeConstructor() {
        var dt = new DataType("INTEGER", "int4", 32, 0, false, "0");
        assertEquals("INTEGER", dt.jdbcType());
        assertEquals("int4", dt.nativeType());
        assertEquals(32, dt.columnSize());
        assertEquals(0, dt.decimalDigits());
        assertFalse(dt.nullable());
        assertEquals("0", dt.defaultValue());
    }

    @Test
    void dataTypeNullDefaults() {
        var dt = new DataType("VARCHAR", "varchar", null, null, true, null);
        assertNull(dt.columnSize());
        assertNull(dt.decimalDigits());
        assertNull(dt.defaultValue());
        assertTrue(dt.nullable());
    }

    // --- ColumnMetadata ---

    @Test
    void columnMetadataRoundTrip() {
        var dt = new DataType("VARCHAR", "varchar", 255, null, false, null);
        var col = new ColumnMetadata("email", 1, dt, true, false, true, false, "user email");
        assertEquals("email", col.name());
        assertEquals(1, col.ordinalPosition());
        assertTrue(col.primaryKey());
        assertFalse(col.foreignKey());
        assertTrue(col.unique());
        assertFalse(col.autoIncrement());
        assertEquals("user email", col.comment());
    }

    @Test
    void columnMetadataIsPk() {
        var col = new ColumnMetadata("id", 1, new DataType("INTEGER", "int4", null, null, false, null),
                true, false, false, false, null);
        assertTrue(col.primaryKey());
    }

    @Test
    void columnMetadataIsFk() {
        var col = new ColumnMetadata("user_id", 1, new DataType("INTEGER", "int4", null, null, true, null),
                false, true, false, false, null);
        assertTrue(col.foreignKey());
    }

    // --- IndexMetadata ---

    @Test
    void indexMetadataSingleColumn() {
        var idx = new IndexMetadata("idx_email", List.of("email"), true, false, "BTREE");
        assertEquals("idx_email", idx.name());
        assertEquals(List.of("email"), idx.columnNames());
        assertTrue(idx.unique());
        assertEquals("BTREE", idx.indexType());
    }

    @Test
    void indexMetadataComposite() {
        var idx = new IndexMetadata("idx_name_age", List.of("last_name", "first_name", "age"),
                false, false, "BTREE");
        assertEquals(3, idx.columnNames().size());
        assertFalse(idx.unique());
    }

    // --- PrimaryKeyMetadata ---

    @Test
    void primaryKeyMetadataSimple() {
        var pk = new PrimaryKeyMetadata("users_pkey", List.of("id"));
        assertEquals("users_pkey", pk.name());
        assertEquals(1, pk.columnNames().size());
        assertEquals("id", pk.columnNames().getFirst());
    }

    @Test
    void primaryKeyMetadataComposite() {
        var pk = new PrimaryKeyMetadata("order_items_pkey", List.of("order_id", "product_id"));
        assertEquals(2, pk.columnNames().size());
    }

    // --- ForeignKeyMetadata ---

    @Test
    void foreignKeyMetadataSimple() {
        var fk = new ForeignKeyMetadata("fk_user_org", List.of("org_id"),
                "public", "organizations", List.of("id"), "CASCADE", "NO ACTION");
        assertEquals("fk_user_org", fk.name());
        assertEquals("public", fk.referencedSchema());
        assertEquals("organizations", fk.referencedTable());
        assertEquals("CASCADE", fk.deleteRule());
    }

    @Test
    void foreignKeyMetadataComposite() {
        var b = new ForeignKeyMetadata.Builder("fk_composite", "public", "order_items");
        b.columnNames().addAll(List.of("order_id", "product_id"));
        b.referencedColumns().addAll(List.of("id", "id2"));
        b.deleteRule("CASCADE");
        b.updateRule("SET NULL");

        var fk = b.build();
        assertEquals(2, fk.columnNames().size());
        assertEquals(2, fk.referencedColumns().size());
        assertEquals("CASCADE", fk.deleteRule());
        assertEquals("SET NULL", fk.updateRule());
    }

    // --- ConstraintMetadata ---

    @Test
    void constraintMetadata() {
        var c = new ConstraintMetadata("users_email_check", "CHECK",
                "email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$'", List.of("email"));
        assertEquals("users_email_check", c.name());
        assertEquals("CHECK", c.type());
    }

    @Test
    void constraintMetadataUnique() {
        var c = new ConstraintMetadata("users_email_key", "UNIQUE", null, List.of("email"));
        assertNull(c.definition());
    }

    // --- TableStatistics ---

    @Test
    void tableStatisticsRoundTrip() {
        var s = new TableStatistics(10000, 65536, 32768, 32768, 9999, 1);
        assertEquals(10000, s.rowCountEstimate());
        assertEquals(65536, s.totalSizeBytes());
        assertEquals(1, s.deadTuples());
    }

    @Test
    void tableStatisticsUnknown() {
        var s = TableStatistics.unknown();
        assertEquals(0, s.rowCountEstimate());
        assertEquals(0, s.totalSizeBytes());
    }

    // --- SchemaMetadata ---

    @Test
    void schemaMetadataWithTables() {
        var tables = List.of(
                new TableMetadata("users", "TABLE", "public", null, TableStatistics.unknown(),
                        List.of(), List.of(), null, List.of(), List.of()),
                new TableMetadata("orders", "TABLE", "public", null, TableStatistics.unknown(),
                        List.of(), List.of(), null, List.of(), List.of()));
        var schema = new SchemaMetadata("public", tables);
        assertEquals("public", schema.name());
        assertEquals(2, schema.tables().size());
    }

    @Test
    void schemaMetadataEmptyTables() {
        var schema = new SchemaMetadata("information_schema", List.of());
        assertTrue(schema.tables().isEmpty());
    }

    // --- TableMetadata ---

    @Test
    void tableMetadataFull() {
        var col = new ColumnMetadata("id", 1, new DataType("INTEGER", "int4", null, null, false, null),
                true, false, false, false, null);
        var pk = new PrimaryKeyMetadata("users_pkey", List.of("id"));
        var table = new TableMetadata("users", "TABLE", "public", "Users table",
                new TableStatistics(500, 1024, 512, 512, 499, 1),
                List.of(col), List.of(), pk, List.of(), List.of());
        assertEquals("users", table.name());
        assertEquals("TABLE", table.type());
        assertEquals("public", table.schema());
        assertEquals("Users table", table.comment());
    }

    @Test
    void tableMetadataView() {
        var table = new TableMetadata("active_users", "VIEW", "public", null,
                TableStatistics.unknown(), List.of(), List.of(), null, List.of(), List.of());
        assertEquals("VIEW", table.type());
    }

    // --- DatabaseMetadata ---

    @Test
    void databaseMetadata() {
        var schemas = List.of(
                new SchemaMetadata("public", List.of()),
                new SchemaMetadata("audit", List.of()));
        var db = new DatabaseMetadata("mydb", "16.0", "PostgreSQL JDBC", schemas);
        assertEquals("mydb", db.databaseName());
        assertEquals("16.0", db.databaseVersion());
        assertEquals(2, db.schemas().size());
    }

    // --- MetadataResponse ---

    @Test
    void metadataResponseSuccess() {
        var data = List.of("schema1", "schema2");
        var resp = MetadataResponse.of("conn-1", "schemas", data, 50, false);
        assertEquals("conn-1", resp.connectionId());
        assertEquals("schemas", resp.type());
        assertEquals(2, resp.totalCount());
        assertFalse(resp.cached());
        assertNull(resp.error());
    }

    @Test
    void metadataResponseCached() {
        var resp = MetadataResponse.of("conn-1", "tables", List.of(), 0, true);
        assertTrue(resp.cached());
    }

    @Test
    void metadataResponseError() {
        var resp = MetadataResponse.error("conn-1", "schemas", "Connection lost");
        assertEquals("Connection lost", resp.error());
        assertEquals(0, resp.totalCount());
        assertTrue(resp.data().isEmpty());
    }

    // --- Data type conversion (simulated) ---

    @Test
    void varcharToString() {
        assertEquals("VARCHAR", mapType("TEXT", "text"));
        assertEquals("VARCHAR", mapType("NVARCHAR", "nvarchar"));
    }

    @Test
    void numericConversions() {
        assertEquals("INTEGER", mapType("INT4", "int4"));
        assertEquals("BIGINT", mapType("INT8", "int8"));
        assertEquals("SMALLINT", mapType("INT2", "int2"));
        assertEquals("DECIMAL", mapType("NUMERIC", "numeric"));
        assertEquals("FLOAT", mapType("FLOAT8", "float8"));
    }

    @Test
    void temporalConversions() {
        assertEquals("TIMESTAMP", mapType("TIMESTAMP", "timestamp"));
        assertEquals("DATE", mapType("DATE", "date"));
        assertEquals("TIME", mapType("TIME", "time"));
    }

    @Test
    void booleanConversion() {
        assertEquals("BOOLEAN", mapType("BOOL", "bool"));
    }

    @Test
    void jsonConversion() {
        assertEquals("JSON", mapType("JSON", "json"));
        assertEquals("JSONB", mapType("JSONB", "jsonb"));
    }

    @Test
    void unknownTypePassthrough() {
        assertEquals("CITEXT", mapType("CITEXT", "citext"));
        assertEquals("UUID", mapType("UUID", "uuid"));
    }

    private String mapType(String jdbcType, String nativeType) {
        // Simulates the type mapping logic from connector implementations
        if (nativeType == null)
            return jdbcType;
        return switch (nativeType.toUpperCase()) {
            case "INT4", "INTEGER", "SERIAL" -> "INTEGER";
            case "INT8", "BIGSERIAL" -> "BIGINT";
            case "INT2", "SMALLINT" -> "SMALLINT";
            case "NUMERIC", "DECIMAL", "MONEY" -> "DECIMAL";
            case "FLOAT4" -> "FLOAT";
            case "FLOAT8", "DOUBLE" -> "FLOAT";
            case "BOOL" -> "BOOLEAN";
            case "VARCHAR", "TEXT", "CHAR", "NVARCHAR", "BPCHAR" -> "VARCHAR";
            case "JSON", "JSONB" -> nativeType.toUpperCase();
            case "TIMESTAMP", "TIMESTAMPTZ" -> "TIMESTAMP";
            case "DATE" -> "DATE";
            case "TIME", "TIMETZ" -> "TIME";
            default -> jdbcType != null ? jdbcType.toUpperCase() : nativeType.toUpperCase();
        };
    }
}
