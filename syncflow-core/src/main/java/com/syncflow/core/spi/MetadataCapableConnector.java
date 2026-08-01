package com.syncflow.core.spi;

import com.syncflow.core.metadata.ColumnMetadata;
import com.syncflow.core.metadata.ConstraintMetadata;
import com.syncflow.core.metadata.ForeignKeyMetadata;
import com.syncflow.core.metadata.IndexMetadata;
import com.syncflow.core.metadata.PrimaryKeyMetadata;
import com.syncflow.core.metadata.TableMetadata;
import com.syncflow.core.metadata.TableStatistics;

import java.util.List;

public interface MetadataCapableConnector extends Connector {

    /** Full schema list. */
    @Override
    List<String> discoverSchemas(ConnectorContext context);

    /** Table names (default: derive from fetchTables). */
    @Override
    default List<String> discoverTables(ConnectorContext context, String schema) {
        return fetchTables(context, schema).stream()
                .map(TableMetadata::name)
                .toList();
    }

    /** Full table metadata including statistics, columns, indexes. */
    List<TableMetadata> fetchTables(ConnectorContext context, String schema);

    /** Column metadata for a specific table. */
    List<ColumnMetadata> fetchColumns(ConnectorContext context, String schema, String table);

    /** Index metadata for a specific table. */
    List<IndexMetadata> fetchIndexes(ConnectorContext context, String schema, String table);

    /** Primary key metadata. */
    PrimaryKeyMetadata fetchPrimaryKey(ConnectorContext context, String schema, String table);

    /** Foreign key metadata. */
    List<ForeignKeyMetadata> fetchForeignKeys(ConnectorContext context, String schema, String table);

    /** Constraint metadata (check, unique, etc). */
    List<ConstraintMetadata> fetchConstraints(ConnectorContext context, String schema, String table);

    /** Table statistics (row count, sizes). */
    TableStatistics fetchStatistics(ConnectorContext context, String schema, String table);
}
