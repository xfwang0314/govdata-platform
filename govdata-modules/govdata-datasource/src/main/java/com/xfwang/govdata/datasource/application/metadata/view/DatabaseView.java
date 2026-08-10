package com.xfwang.govdata.datasource.application.metadata.view;

import java.io.Serial;
import java.io.Serializable;

/** 数据库 / Schema 元数据视图。 */
public class DatabaseView implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String catalog;
    private final String schema;
    private final String databaseName;
    private final String remarks;

    public DatabaseView(String catalog, String schema, String databaseName, String remarks) {
        this.catalog = catalog;
        this.schema = schema;
        this.databaseName = databaseName;
        this.remarks = remarks;
    }

    public String getCatalog() { return catalog; }
    public String getSchema() { return schema; }
    public String getDatabaseName() { return databaseName; }
    public String getRemarks() { return remarks; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String catalog;
        private String schema;
        private String databaseName;
        private String remarks;

        public Builder catalog(String catalog) { this.catalog = catalog; return this; }
        public Builder schema(String schema) { this.schema = schema; return this; }
        public Builder databaseName(String databaseName) { this.databaseName = databaseName; return this; }
        public Builder remarks(String remarks) { this.remarks = remarks; return this; }
        public DatabaseView build() { return new DatabaseView(catalog, schema, databaseName, remarks); }
    }
}
