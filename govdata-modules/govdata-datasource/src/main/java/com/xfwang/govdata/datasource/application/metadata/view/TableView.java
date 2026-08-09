package com.xfwang.govdata.datasource.application.metadata.view;

import java.io.Serial;
import java.io.Serializable;

/** 数据源表元数据视图。 */
public class TableView implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String catalog;
    private final String schema;
    private final String tableName;
    private final String tableType;
    private final String remarks;

    public TableView(String catalog, String schema, String tableName, String tableType, String remarks) {
        this.catalog = catalog;
        this.schema = schema;
        this.tableName = tableName;
        this.tableType = tableType;
        this.remarks = remarks;
    }

    public String getCatalog() { return catalog; }
    public String getSchema() { return schema; }
    public String getTableName() { return tableName; }
    public String getTableType() { return tableType; }
    public String getRemarks() { return remarks; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String catalog;
        private String schema;
        private String tableName;
        private String tableType;
        private String remarks;

        public Builder catalog(String catalog) { this.catalog = catalog; return this; }
        public Builder schema(String schema) { this.schema = schema; return this; }
        public Builder tableName(String tableName) { this.tableName = tableName; return this; }
        public Builder tableType(String tableType) { this.tableType = tableType; return this; }
        public Builder remarks(String remarks) { this.remarks = remarks; return this; }
        public TableView build() { return new TableView(catalog, schema, tableName, tableType, remarks); }
    }
}
