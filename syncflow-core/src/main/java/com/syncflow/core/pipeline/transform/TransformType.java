package com.syncflow.core.pipeline.transform;

public enum TransformType {
    RENAME, IGNORE, CONSTANT_VALUE, CONCATENATE, SUBSTRING, UPPERCASE, LOWERCASE, TRIM, DEFAULT_VALUE, EXPRESSION,
    /**
     * Whole-row SQL projection executed inside an H2 in-process database.
     * The query must be a {@code SELECT} statement whose {@code FROM} clause
     * references the virtual table {@code __row__}. Every source column is
     * pre-loaded into that table as a VARCHAR column before the query runs.
     *
     * <p>
     * Example:
     *
     * <pre>{@code
     *   SELECT id,
     *          UPPER(first_name) AS first_name,
     *          price * 1.2      AS price
     *   FROM __row__
     * }</pre>
     *
     * <p>
     * Stored in
     * {@link com.syncflow.core.pipeline.transform.TransformationRule#parameters()}
     * under key {@code "query"}.
     */
    SQL_QUERY
}
