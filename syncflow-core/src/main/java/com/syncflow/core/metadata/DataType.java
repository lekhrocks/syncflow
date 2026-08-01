package com.syncflow.core.metadata;

public record DataType(
        String jdbcType,
        String nativeType,
        Integer columnSize,
        Integer decimalDigits,
        boolean nullable,
        String defaultValue) {
}
