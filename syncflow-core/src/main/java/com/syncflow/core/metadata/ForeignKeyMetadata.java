package com.syncflow.core.metadata;

import java.util.ArrayList;
import java.util.List;

public record ForeignKeyMetadata(
        String name,
        List<String> columnNames,
        String referencedSchema,
        String referencedTable,
        List<String> referencedColumns,
        String deleteRule,
        String updateRule) {

    public static class Builder {

        private final String name;
        private final List<String> columnNames = new ArrayList<>();
        private final String referencedSchema;
        private final String referencedTable;
        private final List<String> referencedColumns = new ArrayList<>();
        private String deleteRule;
        private String updateRule;

        public Builder(String name, String referencedSchema, String referencedTable) {
            this.name = name;
            this.referencedSchema = referencedSchema;
            this.referencedTable = referencedTable;
        }

        public List<String> columnNames() {
            return columnNames;
        }
        public List<String> referencedColumns() {
            return referencedColumns;
        }
        public void deleteRule(String rule) {
            this.deleteRule = rule;
        }
        public void updateRule(String rule) {
            this.updateRule = rule;
        }

        public ForeignKeyMetadata build() {
            return new ForeignKeyMetadata(name, List.copyOf(columnNames),
                    referencedSchema, referencedTable, List.copyOf(referencedColumns),
                    deleteRule, updateRule);
        }
    }
}
