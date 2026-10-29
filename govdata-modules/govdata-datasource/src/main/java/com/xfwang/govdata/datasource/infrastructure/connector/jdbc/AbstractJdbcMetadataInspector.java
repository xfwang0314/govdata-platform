package com.xfwang.govdata.datasource.infrastructure.connector.jdbc;

import com.xfwang.govdata.datasource.infrastructure.connector.AbstractConnectorSupport;
import com.xfwang.govdata.datasource.infrastructure.connector.ConnectorMetadataInspector;
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

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * 抽象的 JDBC 元数据探测器。
 *
 * <p>继承自 {@link AbstractConnectorSupport}，专门提供 JDBC 驱动加载、连接获取和标准的元数据查询实现，
 * 不继承连通测试相关的逻辑。</p>
 */
public abstract class AbstractJdbcMetadataInspector extends AbstractConnectorSupport implements ConnectorMetadataInspector {

    protected AbstractJdbcMetadataInspector(List<SecretProvider> secretProviders) {
        super(secretProviders);
    }

    /**
     * 获取连接器配置信息。
     *
     * @return 包含 code, version 和 driverClassName 的配置对象
     */
    protected abstract JdbcConnectorProfile profile();

    protected String connectorCode() {
        return profile().code();
    }

    protected String connectorVersion() {
        return profile().version();
    }

    @Override
    public boolean supports(String connectorCode, String connectorVersion) {
        JdbcConnectorProfile p = profile();
        return p.code().equalsIgnoreCase(connectorCode) && p.version().equals(connectorVersion);
    }

    /**
     * 供子类重写，配置 JDBC 连接属性。
     *
     * @param properties JDBC 属性
     * @param request    探测请求
     */
    protected void configure(Properties properties, MetadataInspectRequest request) {
        // default empty
    }

    /**
     * 默认探测的表类型。
     *
     * @return 包含 "TABLE" 和 "VIEW" 的数组
     */
    protected String[] defaultTableTypes() {
        return new String[]{"TABLE", "VIEW"};
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
                DatabaseMetaData meta = connection.getMetaData();
                List<DatabaseMeta> databases = new ArrayList<>();

                // 优先取 Catalogs
                try (ResultSet rs = meta.getCatalogs()) {
                    while (rs.next()) {
                        String cat = rs.getString("TABLE_CAT");
                        if (cat != null && !cat.isBlank()) {
                            databases.add(DatabaseMeta.builder().catalog(cat).databaseName(cat).build());
                        }
                    }
                }

                // 如果 Catalogs 为空，尝试取 Schemas
                if (databases.isEmpty()) {
                    try (ResultSet rs = meta.getSchemas()) {
                        while (rs.next()) {
                            String schema = rs.getString("TABLE_SCHEM");
                            String cat = rs.getString("TABLE_CATALOG");
                            if (schema != null && !schema.isBlank()) {
                                databases.add(DatabaseMeta.builder().catalog(cat).schema(schema).databaseName(schema).build());
                            }
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
                DatabaseMetaData meta = connection.getMetaData();
                String[] types = request.getTableType() == null || request.getTableType().isBlank() 
                        ? defaultTableTypes() : new String[]{request.getTableType()};
                String pattern = request.getTableNamePattern() == null || request.getTableNamePattern().isBlank()
                        ? null : request.getTableNamePattern();

                int limit = Math.min(Math.max(request.getLimit(), 1), 1000);
                int offset = Math.max(request.getOffset(), 0);

                List<TableMeta> pagedTables = new ArrayList<>();
                long total = 0;

                try (ResultSet rs = meta.getTables(request.getCatalog(), request.getSchema(), pattern, types)) {
                    while (rs.next()) {
                        if (total < offset) {
                            total++;
                            continue;
                        }
                        if (pagedTables.size() < limit) {
                            pagedTables.add(TableMeta.builder()
                                    .catalog(rs.getString("TABLE_CAT"))
                                    .schema(rs.getString("TABLE_SCHEM"))
                                    .tableName(rs.getString("TABLE_NAME"))
                                    .tableType(rs.getString("TABLE_TYPE"))
                                    .remarks(rs.getString("REMARKS"))
                                    .build());
                        }
                        total++;
                    }
                }

                return MetadataInspectResult.builder()
                        .success(true)
                        .reasonCode("SUCCESS")
                        .latencyMs(elapsed(start))
                        .tables(pagedTables)
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
                DatabaseMetaData meta = connection.getMetaData();
                List<TableSchema> schemas = new ArrayList<>();

                for (String tableName : tableNames) {
                    List<ColumnMeta> columns = new ArrayList<>();
                    Set<String> pkNames = new HashSet<>();
                    
                    try (ResultSet rsPk = meta.getPrimaryKeys(request.getCatalog(), request.getSchema(), tableName)) {
                        while (rsPk.next()) {
                            pkNames.add(rsPk.getString("COLUMN_NAME"));
                        }
                    }

                    try (ResultSet rs = meta.getColumns(request.getCatalog(), request.getSchema(), tableName, null)) {
                        while (rs.next()) {
                            String colName = rs.getString("COLUMN_NAME");
                            String isNullableStr = rs.getString("IS_NULLABLE");
                            boolean nullable = "YES".equalsIgnoreCase(isNullableStr);
                            columns.add(ColumnMeta.builder()
                                    .columnName(colName)
                                    .dataType(rs.getString("TYPE_NAME"))
                                    .jdbcType(rs.getInt("DATA_TYPE"))
                                    .columnSize(rs.getInt("COLUMN_SIZE"))
                                    .decimalDigits(rs.getInt("DECIMAL_DIGITS"))
                                    .nullable(nullable)
                                    .defaultValue(rs.getString("COLUMN_DEF"))
                                    .remarks(rs.getString("REMARKS"))
                                    .ordinalPosition(rs.getInt("ORDINAL_POSITION"))
                                    .primaryKey(pkNames.contains(colName))
                                    .build());
                        }
                    }
                    
                    TableMeta tableMeta = TableMeta.builder()
                            .catalog(request.getCatalog())
                            .schema(request.getSchema())
                            .tableName(tableName)
                            .build();

                    schemas.add(TableSchema.builder()
                            .table(tableMeta)
                            .columns(columns)
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
}
