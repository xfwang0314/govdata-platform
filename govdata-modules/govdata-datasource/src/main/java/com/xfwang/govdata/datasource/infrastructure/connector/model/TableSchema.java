package com.xfwang.govdata.datasource.infrastructure.connector.model;

import java.util.List;

/** 数据源表结构描述（表元数据 + 字段列表）。 */
public class TableSchema {
    private final TableMeta table;
    private final List<ColumnMeta> columns;

    public TableSchema(TableMeta table, List<ColumnMeta> columns) {
        this.table = table;
        this.columns = columns;
    }

    public TableMeta getTable() { return table; }
    public List<ColumnMeta> getColumns() { return columns; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private TableMeta table;
        private List<ColumnMeta> columns;

        public Builder table(TableMeta table) { this.table = table; return this; }
        public Builder columns(List<ColumnMeta> columns) { this.columns = columns; return this; }
        public TableSchema build() { return new TableSchema(table, columns); }
    }
}
