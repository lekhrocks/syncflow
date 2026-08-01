package com.syncflow.core.metadata;

import java.util.List;

public record PrimaryKeyMetadata(
        String name,
        List<String> columnNames) {
}
