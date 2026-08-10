package com.xfwang.govdata.datasource.infrastructure.connector.model;

import java.util.List;

/**
 * 元数据探测结果。
 */
public record MetadataInspectResult(boolean success, String reasonCode, String message, long latencyMs,
                                    List<DatabaseMeta> databases, List<TableMeta> tables,
                                    List<TableSchema> tableSchemas, long total) {
    public MetadataInspectResult(boolean success, String reasonCode, String message, long latencyMs,
                                 List<DatabaseMeta> databases, List<TableMeta> tables,
                                 List<TableSchema> tableSchemas, long total) {
        this.success = success;
        this.reasonCode = reasonCode;
        this.message = message;
        this.latencyMs = latencyMs;
        this.databases = databases == null ? List.of() : databases;
        this.tables = tables == null ? List.of() : tables;
        this.tableSchemas = tableSchemas == null ? List.of() : tableSchemas;
        this.total = total;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean success;
        private String reasonCode;
        private String message;
        private long latencyMs;
        private List<DatabaseMeta> databases = List.of();
        private List<TableMeta> tables = List.of();
        private List<TableSchema> tableSchemas = List.of();
        private long total;

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder reasonCode(String reasonCode) {
            this.reasonCode = reasonCode;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder latencyMs(long latencyMs) {
            this.latencyMs = latencyMs;
            return this;
        }

        public Builder databases(List<DatabaseMeta> databases) {
            this.databases = databases;
            return this;
        }

        public Builder tables(List<TableMeta> tables) {
            this.tables = tables;
            return this;
        }

        public Builder tableSchemas(List<TableSchema> tableSchemas) {
            this.tableSchemas = tableSchemas;
            return this;
        }

        public Builder total(long total) {
            this.total = total;
            return this;
        }

        public MetadataInspectResult build() {
            return new MetadataInspectResult(success, reasonCode, message, latencyMs, databases, tables, tableSchemas, total);
        }
    }
}
