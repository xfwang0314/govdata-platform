package com.xfwang.govdata.datasource.infrastructure.connector.mysql;

import com.xfwang.govdata.datasource.infrastructure.connector.jdbc.AbstractJdbcMetadataInspector;
import com.xfwang.govdata.datasource.infrastructure.connector.jdbc.JdbcConnectorProfile;
import com.xfwang.govdata.datasource.infrastructure.connector.model.ColumnMeta;
import com.xfwang.govdata.datasource.infrastructure.connector.model.DatabaseMeta;
import com.xfwang.govdata.datasource.infrastructure.connector.model.MetadataInspectRequest;
import com.xfwang.govdata.datasource.infrastructure.connector.model.MetadataInspectResult;
import com.xfwang.govdata.datasource.infrastructure.connector.model.TableMeta;
import com.xfwang.govdata.datasource.infrastructure.connector.model.TableSchema;
import com.xfwang.govdata.datasource.infrastructure.connector.support.ConnectionFailureClassifier;
import com.xfwang.platform.api.secret.SecretProvider;
import com.xfwang.platform.api.secret.SecretType;
import com.xfwang.platform.api.secret.SecretValue;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * MySQL 元数据探测 Inspector。
 *
 * <p>通过直接查询 MySQL {@code information_schema} 系统表，实现数据库/Schema 探测、引擎级
 * {@code LIMIT / OFFSET} 下推分页和 1 次 SQL 批量拉取多表字段，提供极高的探测性能。</p>
 */
@Component
public class MySqlMetadataInspector extends AbstractJdbcMetadataInspector {

    private static final List<String> SYSTEM_DATABASES = List.of(
            "information_schema", "performance_schema", "mysql", "sys"
    );

    public MySqlMetadataInspector(List<SecretProvider> providers) {
        super(providers);
    }

    @Override
    protected JdbcConnectorProfile profile() {
        return JdbcConnectorProfile.MYSQL;
    }

    @Override
    protected void configure(Properties properties, MetadataInspectRequest request) {
        properties.putIfAbsent("connectTimeout", "5000");
        properties.putIfAbsent("socketTimeout", "30000");
        properties.putIfAbsent("useInformationSchema", "true");
    }

    @Override
    public MetadataInspectResult listDatabases(MetadataInspectRequest request) {
        long start = System.currentTimeMillis();
        var secretOpt = resolveSecret(request, SecretType.USERNAME_PASSWORD, Set.of("username", "password"));
        if (secretOpt.isEmpty()) {
            return MetadataInspectResult.builder()
                    .success(false)
                    .reasonCode("SECRET_UNAVAILABLE")
                    .message("无法获取数据源凭据")
                    .latencyMs(0)
                    .build();
        }

        try (SecretValue secret = secretOpt.get()) {
            String jdbcUrl = connectionTarget(request.getEndpointConfig());
            Class.forName(profile().driverClassName());

            Properties props = new Properties();
            if (secret.get("username") != null) props.setProperty("user", new String(secret.get("username")));
            if (secret.get("password") != null) props.setProperty("password", new String(secret.get("password")));
            Map<String, String> driverProps = driverProperties(request.getEndpointConfig());
            if (driverProps != null) {
                props.putAll(driverProps);
            }
            configure(props, request);

            try (Connection connection = DriverManager.getConnection(jdbcUrl, props)) {
                String sql = "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA ORDER BY SCHEMA_NAME";
                List<DatabaseMeta> databases = new ArrayList<>();

                try (PreparedStatement ps = connection.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String dbName = rs.getString("SCHEMA_NAME");
                        if (!SYSTEM_DATABASES.contains(dbName.toLowerCase())) {
                            databases.add(DatabaseMeta.builder().catalog(dbName).databaseName(dbName).build());
                        }
                    }
                }

                return MetadataInspectResult.builder()
                        .success(true)
                        .reasonCode("SUCCESS")
                        .latencyMs(elapsed(start))
                        .databases(databases)
                        .total(databases.size())
                        .build();
            }
        } catch (Throwable e) {
            String reasonCode = ConnectionFailureClassifier.classify(e).code();
            return MetadataInspectResult.builder()
                    .success(false)
                    .reasonCode(reasonCode)
                    .message(e.getMessage())
                    .latencyMs(elapsed(start))
                    .build();
        }
    }

    @Override
    public MetadataInspectResult listTables(MetadataInspectRequest request) {
        long start = System.currentTimeMillis();
        var secretOpt = resolveSecret(request, SecretType.USERNAME_PASSWORD, Set.of("username", "password"));
        if (secretOpt.isEmpty()) {
            return MetadataInspectResult.builder()
                    .success(false)
                    .reasonCode("SECRET_UNAVAILABLE")
                    .message("无法获取数据源凭据")
                    .latencyMs(0)
                    .build();
        }

        try (SecretValue secret = secretOpt.get()) {
            String jdbcUrl = connectionTarget(request.getEndpointConfig());
            Class.forName(profile().driverClassName());

            Properties props = new Properties();
            if (secret.get("username") != null) props.setProperty("user", new String(secret.get("username")));
            if (secret.get("password") != null) props.setProperty("password", new String(secret.get("password")));
            Map<String, String> driverProps = driverProperties(request.getEndpointConfig());
            if (driverProps != null) {
                props.putAll(driverProps);
            }
            configure(props, request);

            try (Connection connection = DriverManager.getConnection(jdbcUrl, props)) {
                String databaseName = requestedDatabase(request);
                String scopePredicate = databaseName == null
                    ? "TABLE_SCHEMA NOT IN (" + String.join(",",
                        Collections.nCopies(SYSTEM_DATABASES.size(), "?")) + ")"
                    : "TABLE_SCHEMA = ?";
                StringBuilder sqlCount = new StringBuilder(
                        "SELECT COUNT(*) FROM information_schema.TABLES WHERE " + scopePredicate);
                StringBuilder sqlPage = new StringBuilder(
                        "SELECT TABLE_SCHEMA, TABLE_NAME, TABLE_TYPE, TABLE_COMMENT " +
                        "FROM information_schema.TABLES WHERE " + scopePredicate);

                List<Object> countParams = new ArrayList<>();
                List<Object> pageParams = new ArrayList<>();
                if (databaseName != null) {
                    countParams.add(databaseName);
                    pageParams.add(databaseName);
                } else {
                    countParams.addAll(SYSTEM_DATABASES);
                    pageParams.addAll(SYSTEM_DATABASES);
                }

                if (request.getTableNamePattern() != null && !request.getTableNamePattern().isBlank()) {
                    sqlCount.append(" AND TABLE_NAME LIKE ?");
                    sqlPage.append(" AND TABLE_NAME LIKE ?");
                    countParams.add(request.getTableNamePattern());
                    pageParams.add(request.getTableNamePattern());
                }

                if (request.getTableType() != null && !request.getTableType().isBlank()) {
                    sqlCount.append(" AND TABLE_TYPE = ?");
                    sqlPage.append(" AND TABLE_TYPE = ?");
                    countParams.add(request.getTableType());
                    pageParams.add(request.getTableType());
                }

                // 计数
                long total = 0;
                try (PreparedStatement psCount = connection.prepareStatement(sqlCount.toString())) {
                    for (int i = 0; i < countParams.size(); i++) {
                        psCount.setObject(i + 1, countParams.get(i));
                    }
                    try (ResultSet rsCount = psCount.executeQuery()) {
                        if (rsCount.next()) {
                            total = rsCount.getLong(1);
                        }
                    }
                }

                // 分页查询
                int limit = Math.min(Math.max(request.getLimit(), 1), 1000);
                int offset = Math.max(request.getOffset(), 0);
                sqlPage.append(" ORDER BY TABLE_NAME LIMIT ? OFFSET ?");
                pageParams.add(limit);
                pageParams.add(offset);

                List<TableMeta> tables = new ArrayList<>();
                try (PreparedStatement psPage = connection.prepareStatement(sqlPage.toString())) {
                    for (int i = 0; i < pageParams.size(); i++) {
                        psPage.setObject(i + 1, pageParams.get(i));
                    }
                    try (ResultSet rs = psPage.executeQuery()) {
                        while (rs.next()) {
                            tables.add(TableMeta.builder()
                                    .catalog(rs.getString("TABLE_SCHEMA"))
                                    .tableName(rs.getString("TABLE_NAME"))
                                    .tableType(rs.getString("TABLE_TYPE"))
                                    .remarks(rs.getString("TABLE_COMMENT"))
                                    .build());
                        }
                    }
                }

                return MetadataInspectResult.builder()
                        .success(true)
                        .reasonCode("SUCCESS")
                        .latencyMs(elapsed(start))
                        .tables(tables)
                        .total(total)
                        .build();
            }
        } catch (Throwable e) {
            String reasonCode = ConnectionFailureClassifier.classify(e).code();
            return MetadataInspectResult.builder()
                    .success(false)
                    .reasonCode(reasonCode)
                    .message(e.getMessage())
                    .latencyMs(elapsed(start))
                    .build();
        }
    }

    @Override
    public MetadataInspectResult inspectColumns(MetadataInspectRequest request, List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty() || tableNames.size() > 500) {
            return MetadataInspectResult.builder()
                    .success(false)
                    .reasonCode("INVALID_ARGUMENT")
                    .message("tableNames 数量必须在 1 到 500 之间")
                    .latencyMs(0)
                    .build();
        }

        long start = System.currentTimeMillis();
        var secretOpt = resolveSecret(request, SecretType.USERNAME_PASSWORD, Set.of("username", "password"));
        if (secretOpt.isEmpty()) {
            return MetadataInspectResult.builder()
                    .success(false)
                    .reasonCode("SECRET_UNAVAILABLE")
                    .message("无法获取数据源凭据")
                    .latencyMs(0)
                    .build();
        }

        try (SecretValue secret = secretOpt.get()) {
            String jdbcUrl = connectionTarget(request.getEndpointConfig());
            Class.forName(profile().driverClassName());

            Properties props = new Properties();
            if (secret.get("username") != null) props.setProperty("user", new String(secret.get("username")));
            if (secret.get("password") != null) props.setProperty("password", new String(secret.get("password")));
            Map<String, String> driverProps = driverProperties(request.getEndpointConfig());
            if (driverProps != null) {
                props.putAll(driverProps);
            }
            configure(props, request);

            try (Connection connection = DriverManager.getConnection(jdbcUrl, props)) {
                String databaseName = targetDatabase(request, connection);
                // 动态构建 IN (?, ?, ...)
                String placeholders = String.join(",", Collections.nCopies(tableNames.size(), "?"));
                String sql = "SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT, ORDINAL_POSITION, COLUMN_KEY " +
                             "FROM information_schema.COLUMNS " +
                             "WHERE TABLE_SCHEMA = ? AND TABLE_NAME IN (" + placeholders + ") " +
                             "ORDER BY TABLE_NAME, ORDINAL_POSITION";

                Map<String, List<ColumnMeta>> columnMap = new LinkedHashMap<>();
                for (String tName : tableNames) {
                    columnMap.put(tName, new ArrayList<>());
                }

                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, databaseName);
                    for (int i = 0; i < tableNames.size(); i++) {
                        ps.setString(i + 2, tableNames.get(i));
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String tName = rs.getString("TABLE_NAME");
                            String colName = rs.getString("COLUMN_NAME");
                            boolean nullable = "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE"));
                            boolean isPk = "PRI".equalsIgnoreCase(rs.getString("COLUMN_KEY"));

                            ColumnMeta colMeta = ColumnMeta.builder()
                                    .columnName(colName)
                                    .dataType(rs.getString("DATA_TYPE"))
                                    .jdbcType(0) // information_schema 查询下类型名优先
                                    .nullable(nullable)
                                    .defaultValue(rs.getString("COLUMN_DEFAULT"))
                                    .remarks(rs.getString("COLUMN_COMMENT"))
                                    .ordinalPosition(rs.getInt("ORDINAL_POSITION"))
                                    .primaryKey(isPk)
                                    .build();

                            List<ColumnMeta> cols = columnMap.get(tName);
                            if (cols != null) {
                                cols.add(colMeta);
                            }
                        }
                    }
                }

                List<TableSchema> schemas = new ArrayList<>();
                for (Map.Entry<String, List<ColumnMeta>> entry : columnMap.entrySet()) {
                    schemas.add(TableSchema.builder()
                            .table(TableMeta.builder().catalog(databaseName).tableName(entry.getKey()).build())
                            .columns(entry.getValue())
                            .build());
                }

                return MetadataInspectResult.builder()
                        .success(true)
                        .reasonCode("SUCCESS")
                        .latencyMs(elapsed(start))
                        .tableSchemas(schemas)
                        .total(schemas.size())
                        .build();
            }
        } catch (Throwable e) {
            String reasonCode = ConnectionFailureClassifier.classify(e).code();
            return MetadataInspectResult.builder()
                    .success(false)
                    .reasonCode(reasonCode)
                    .message(e.getMessage())
                    .latencyMs(elapsed(start))
                    .build();
        }
    }

    private String connectionTarget(Map<String, Object> endpointConfig) {
        if (endpointConfig == null) return "";
        Object target = endpointConfig.get("connectionTarget");
        if (target != null && !target.toString().isBlank()) return target.toString().trim();
        Object jdbcUrl = endpointConfig.get("jdbcUrl");
        return jdbcUrl == null ? "" : jdbcUrl.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> driverProperties(Map<String, Object> endpointConfig) {
        if (endpointConfig == null) return Map.of();
        Object value = endpointConfig.get("properties");
        return value instanceof Map<?, ?> map ? (Map<String, String>) map : Map.of();
    }

    /** MySQL 将 database 映射为 Catalog，不重复生成同名 Schema。 */
    private String targetDatabase(MetadataInspectRequest request, Connection connection) throws java.sql.SQLException {
        String requested = requestedDatabase(request);
        if (requested != null) return requested;
        String databaseName = connection.getCatalog();
        if (databaseName == null || databaseName.isBlank()) {
            throw new java.sql.SQLException("MySQL连接未指定可探测的数据库");
        }
        return databaseName;
    }

    private String requestedDatabase(MetadataInspectRequest request) {
        if (request.getCatalog() != null && !request.getCatalog().isBlank()) return request.getCatalog();
        if (request.getSchema() != null && !request.getSchema().isBlank()) return request.getSchema();
        return null;
    }
}
