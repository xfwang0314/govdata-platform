package com.xfwang.govdata.datasource.infrastructure.connector.model;

/** 数据源字段元数据描述。 */
public class ColumnMeta {
    private final String columnName;
    private final String dataType;
    private final int jdbcType;
    private final int columnSize;
    private final int decimalDigits;
    private final boolean nullable;
    private final boolean primaryKey;
    private final String defaultValue;
    private final String remarks;
    private final int ordinalPosition;

    public ColumnMeta(String columnName, String dataType, int jdbcType, int columnSize, int decimalDigits,
                      boolean nullable, boolean primaryKey, String defaultValue, String remarks, int ordinalPosition) {
        this.columnName = columnName;
        this.dataType = dataType;
        this.jdbcType = jdbcType;
        this.columnSize = columnSize;
        this.decimalDigits = decimalDigits;
        this.nullable = nullable;
        this.primaryKey = primaryKey;
        this.defaultValue = defaultValue;
        this.remarks = remarks;
        this.ordinalPosition = ordinalPosition;
    }

    public String getColumnName() { return columnName; }
    public String getDataType() { return dataType; }
    public int getJdbcType() { return jdbcType; }
    public int getColumnSize() { return columnSize; }
    public int getDecimalDigits() { return decimalDigits; }
    public boolean isNullable() { return nullable; }
    public boolean isPrimaryKey() { return primaryKey; }
    public String getDefaultValue() { return defaultValue; }
    public String getRemarks() { return remarks; }
    public int getOrdinalPosition() { return ordinalPosition; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String columnName;
        private String dataType;
        private int jdbcType;
        private int columnSize;
        private int decimalDigits;
        private boolean nullable;
        private boolean primaryKey;
        private String defaultValue;
        private String remarks;
        private int ordinalPosition;

        public Builder columnName(String columnName) { this.columnName = columnName; return this; }
        public Builder dataType(String dataType) { this.dataType = dataType; return this; }
        public Builder jdbcType(int jdbcType) { this.jdbcType = jdbcType; return this; }
        public Builder columnSize(int columnSize) { this.columnSize = columnSize; return this; }
        public Builder decimalDigits(int decimalDigits) { this.decimalDigits = decimalDigits; return this; }
        public Builder nullable(boolean nullable) { this.nullable = nullable; return this; }
        public Builder primaryKey(boolean primaryKey) { this.primaryKey = primaryKey; return this; }
        public Builder defaultValue(String defaultValue) { this.defaultValue = defaultValue; return this; }
        public Builder remarks(String remarks) { this.remarks = remarks; return this; }
        public Builder ordinalPosition(int ordinalPosition) { this.ordinalPosition = ordinalPosition; return this; }
        public ColumnMeta build() {
            return new ColumnMeta(columnName, dataType, jdbcType, columnSize, decimalDigits,
                    nullable, primaryKey, defaultValue, remarks, ordinalPosition);
        }
    }
}
