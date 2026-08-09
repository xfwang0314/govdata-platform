package com.xfwang.govdata.datasource.application.metadata.view;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** 数据源表结构视图（表元数据 + 全部字段）。 */
public class TableSchemaView implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final TableView table;
    private final List<ColumnView> columns;

    public TableSchemaView(TableView table, List<ColumnView> columns) {
        this.table = table;
        this.columns = columns;
    }

    public TableView getTable() { return table; }
    public List<ColumnView> getColumns() { return columns; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private TableView table;
        private List<ColumnView> columns;

        public Builder table(TableView table) { this.table = table; return this; }
        public Builder columns(List<ColumnView> columns) { this.columns = columns; return this; }
        public TableSchemaView build() { return new TableSchemaView(table, columns); }
    }
}
