package com.xfwang.govdata.datasource.infrastructure.connector.jdbc;

/**
 * JDBC 连接器配置描述。
 * 包含连接器代码、版本和驱动类名。
 */
public record JdbcConnectorProfile(String code, String version, String driverClassName) {
    /**
     * MySQL 默认配置
     */
    public static final JdbcConnectorProfile MYSQL = new JdbcConnectorProfile("mysql", "1.0", "com.mysql.cj.jdbc.Driver");
    /**
     * Oracle 默认配置
     */
    public static final JdbcConnectorProfile ORACLE = new JdbcConnectorProfile("oracle", "1.0", "oracle.jdbc.OracleDriver");
    /**
     * PostgreSQL 默认配置
     */
    public static final JdbcConnectorProfile POSTGRESQL = new JdbcConnectorProfile("postgresql", "1.0", "org.postgresql.Driver");
    /**
     * ClickHouse 默认配置
     */
    public static final JdbcConnectorProfile CLICKHOUSE = new JdbcConnectorProfile("clickhouse", "1.0", "com.clickhouse.jdbc.ClickHouseDriver");
}
