package com.xfwang.govdata.datasource.infrastructure.connector.mysql;

import com.xfwang.govdata.api.query.spi.AuthorizedExecutionPlan;
import com.xfwang.govdata.api.query.spi.QueryFieldSchema;
import com.xfwang.govdata.api.query.spi.RawQueryResult;
import com.xfwang.govdata.api.query.QueryOperator;
import com.xfwang.govdata.datasource.domain.exception.DataSourceErrorCode;
import com.xfwang.platform.api.secret.SecretValue;
import org.dromara.common.core.exception.ServiceException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** MySQL 参数化执行边界；不记录驱动异常、连接信息、绑定值或数据。 */
public final class MySqlReadOnlyQueryExecutor {
    private static final Set<String> SYSTEM_DATABASES = Set.of("mysql", "sys", "information_schema", "performance_schema");
    private static final java.util.concurrent.ScheduledThreadPoolExecutor DEADLINES = deadlineExecutor();

    private MySqlReadOnlyQueryExecutor() { }

    public static RawQueryResult execute(AuthorizedExecutionPlan plan, Map<String, Object> endpoint, SecretValue secret,
                                         String environment) {
        if (plan.getLimit() == null || plan.getLimit() < 1 || plan.getLimit() > 1000
            || plan.getTimeoutMs() == null || plan.getTimeoutMs() < 1 || plan.getTimeoutMs() > 30000
            || plan.getOffset() == null || plan.getOffset() < 0 || plan.getOffset() > 10000
            || plan.getSelectedFields().isEmpty() || plan.getSelectedFields().size() > 100) throw invalid();
        String host = String.valueOf(endpoint.get("host"));
        String database = String.valueOf(endpoint.get("database"));
        String port = String.valueOf(endpoint.get("port"));
        if (!host.matches("[a-zA-Z0-9.-]+") || !port.matches("[0-9]{1,5}")
            || Integer.parseInt(port) < 1 || Integer.parseInt(port) > 65535
            || !database.equals(plan.getResource().getCatalogName())
            || SYSTEM_DATABASES.contains(database.toLowerCase(Locale.ROOT))
            || plan.getResource().getSchemaName() != null && !plan.getResource().getSchemaName().isBlank()) throw invalid();
        quote(database);
        char[] username = secret.get("username");
        char[] password = secret.get("password");
        if (username == null || password == null) throw new ServiceException(DataSourceErrorCode.SECRET_UNAVAILABLE);
        Properties properties = new Properties();
        properties.setProperty("user", new String(username));
        properties.setProperty("password", new String(password));
        Arrays.fill(username, '\0');
        Arrays.fill(password, '\0');
        properties.setProperty("connectTimeout", String.valueOf(Math.min(5000, plan.getTimeoutMs())));
        properties.setProperty("socketTimeout", plan.getTimeoutMs().toString());
        properties.setProperty("allowMultiQueries", "false");
        properties.setProperty("allowLoadLocalInfile", "false");
        properties.setProperty("autoDeserialize", "false");
        properties.setProperty("useServerPrepStmts", "true");
        properties.setProperty("useCursorFetch", "true");
        properties.setProperty("connectionTimeZone", "Asia/Shanghai");
        // 测试/开发允许服务器自签证书但仍强制加密；生产必须验证证书链和主机名。
        properties.setProperty("sslMode", "DEV".equals(environment) || "TEST".equals(environment)
            ? "REQUIRED" : "VERIFY_IDENTITY");
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(plan.getTimeoutMs());
        var expired = new java.util.concurrent.atomic.AtomicBoolean();
        try (Connection connection = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/"
            + java.net.URLEncoder.encode(database, java.nio.charset.StandardCharsets.UTF_8), properties)) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new ServiceException(DataSourceErrorCode.QUERY_TIMEOUT);
            var cancellation = DEADLINES.schedule(() -> {
                expired.set(true);
                try { connection.abort(Runnable::run); } catch (SQLException ignored) { /* 原始异常不外泄。 */ }
            }, remaining, java.util.concurrent.TimeUnit.NANOSECONDS);
            try {
            requireReadOnlyGrants(connection);
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try (Statement settings = connection.createStatement()) {
                settings.setQueryTimeout(Math.max(1, (plan.getTimeoutMs() + 999) / 1000));
                settings.execute("SET SESSION TRANSACTION READ ONLY");
                settings.execute("SET SESSION MAX_EXECUTION_TIME = " + plan.getTimeoutMs());
            }
            RawQueryResult result = select(connection, plan);
            connection.rollback();
            if (expired.get()) throw new ServiceException(DataSourceErrorCode.QUERY_TIMEOUT);
            return result;
            } finally {
                cancellation.cancel(false);
            }
        } catch (SQLException failure) {
            if (expired.get() || timedOut(failure) || failure.getErrorCode() == 3024
                || failure.getErrorCode() == 1317 || "S1T00".equals(failure.getSQLState())) {
                throw new ServiceException(DataSourceErrorCode.QUERY_TIMEOUT);
            }
            if (failure.getSQLState() != null && failure.getSQLState().startsWith("08"))
                throw new ServiceException(DataSourceErrorCode.CONNECTION_FAILED);
            if (failure.getSQLState() != null && failure.getSQLState().startsWith("28")) forbidden();
            throw new ServiceException(DataSourceErrorCode.QUERY_FAILED);
        } finally {
            properties.clear();
        }
    }

    private static void requireReadOnlyGrants(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(5);
            statement.setMaxRows(101);
            try (ResultSet role = statement.executeQuery("SELECT CURRENT_ROLE()")) {
                if (!role.next() || !"NONE".equals(role.getString(1))) forbidden();
            }
            try (ResultSet grants = statement.executeQuery("SHOW GRANTS")) {
                int count = 0;
                while (grants.next()) {
                    String grant = grants.getString(1);
                    if (++count > 100 || !grant.matches("(?s)^GRANT (USAGE|SELECT) ON .+ TO .+$")
                        || grant.contains(" WITH GRANT OPTION")) forbidden();
                }
                if (count == 0) forbidden();
            }
        }
    }

    private static RawQueryResult select(Connection connection, AuthorizedExecutionPlan plan) throws SQLException {
        String database = plan.getResource().getCatalogName();
        String table = plan.getResource().getObjectName();
        Map<Long, QueryFieldSchema> fields = new LinkedHashMap<>();
        for (var field : plan.getResource().getFields()) {
            if (field.getFieldId() == null || fields.put(field.getFieldId(), field) != null) throw invalid();
            quote(field.getPhysicalName());
        }
        List<String> columns = new ArrayList<>();
        for (var field : plan.getSelectedFields()) {
            if (!field.equals(fields.get(field.getFieldId())) || !Boolean.TRUE.equals(field.getSelectable())) throw invalid();
            columns.add(quote(field.getPhysicalName()));
        }
        StringBuilder sql = new StringBuilder("SELECT ").append(String.join(", ", columns))
            .append(" FROM ").append(quote(database)).append('.').append(quote(table));
        Map<String, Integer> columnTypes = new LinkedHashMap<>();
        String pattern = table.replace("\\", "\\\\").replace("_", "\\_").replace("%", "\\%");
        try (ResultSet metadata = connection.getMetaData().getColumns(database, null, pattern, "%")) {
            while (metadata.next()) {
                if (table.equals(metadata.getString("TABLE_NAME"))) {
                    columnTypes.put(metadata.getString("COLUMN_NAME"), metadata.getInt("DATA_TYPE"));
                }
            }
        }
        List<Binding> bindings = new ArrayList<>();
        List<String> predicates = new ArrayList<>();
        if (plan.getFilters().size() > 50 || plan.getSorts().size() > 5) throw invalid();
        for (var filter : plan.getFilters()) {
            if (filter == null || filter.getOperator() == null || !fields.containsKey(filter.getFieldId())) throw invalid();
            String name = fields.get(filter.getFieldId()).getPhysicalName();
            Integer type = columnTypes.get(name);
            if (type == null) throw invalid();
            var operator = filter.getOperator();
            List<Object> values = filter.getValues();
            if (values == null) throw invalid();
            if (operator == QueryOperator.IS_NULL || operator == QueryOperator.IS_NOT_NULL) {
                if (!values.isEmpty()) throw invalid();
                predicates.add(quote(name) + (operator == QueryOperator.IS_NULL ? " IS NULL" : " IS NOT NULL"));
                continue;
            }
            boolean multiple = operator == QueryOperator.IN || operator == QueryOperator.NOT_IN;
            if (values.isEmpty() || values.size() > (multiple ? 500 : 1)) throw invalid();
            String token = switch (operator) {
                case EQ -> "=";
                case NE -> "<>";
                case GT -> ">";
                case GTE -> ">=";
                case LT -> "<";
                case LTE -> "<=";
                case IN -> "IN";
                case NOT_IN -> "NOT IN";
                case LIKE -> "LIKE";
                default -> throw invalid();
            };
            for (Object value : values) {
                if (operator == QueryOperator.LIKE && (!(value instanceof String text) || text.isEmpty()
                    || text.startsWith("%") || text.startsWith("_") || !isText(type))) throw invalid();
                bindings.add(new Binding(typed(value, type), type));
            }
            String placeholders = String.join(", ", java.util.Collections.nCopies(values.size(), "?"));
            predicates.add(quote(name) + " " + token + " " + (multiple ? "(" + placeholders + ")" : "?"));
        }
        if (!predicates.isEmpty()) sql.append(" WHERE (").append(String.join(") AND (", predicates)).append(')');
        List<String> order = new ArrayList<>();
        Set<Long> sorted = new java.util.HashSet<>();
        for (var sort : plan.getSorts()) {
            if (sort == null || sort.getDirection() == null || !fields.containsKey(sort.getFieldId())
                || !sorted.add(sort.getFieldId())) throw invalid();
            order.add(quote(fields.get(sort.getFieldId()).getPhysicalName()) + " " + sort.getDirection().name());
        }
        try (ResultSet keys = connection.getMetaData().getPrimaryKeys(database, null, table)) {
            Map<Short, String> keyOrder = new java.util.TreeMap<>();
            while (keys.next()) keyOrder.put(keys.getShort("KEY_SEQ"), keys.getString("COLUMN_NAME"));
            if (keyOrder.isEmpty()) throw invalid();
            for (String key : keyOrder.values()) {
                if (fields.values().stream().noneMatch(field -> key.equals(field.getPhysicalName()))) throw invalid();
                order.add(quote(key) + " ASC");
            }
        }
        if (order.isEmpty()) throw invalid();
        sql.append(" ORDER BY ").append(String.join(", ", order)).append(" LIMIT ? OFFSET ?");
        try (var statement = connection.prepareStatement(sql.toString())) {
            int parameter = 1;
            for (Binding binding : bindings) statement.setObject(parameter++, binding.value(), binding.type());
            statement.setInt(parameter++, plan.getLimit() + 1);
            statement.setInt(parameter, plan.getOffset());
            statement.setMaxRows(plan.getLimit() + 1);
            statement.setFetchSize(Math.min(100, plan.getLimit() + 1));
            statement.setQueryTimeout(Math.max(1, (plan.getTimeoutMs() + 999) / 1000));
            List<Map<Long, Object>> rows = new ArrayList<>();
            long resultBytes = 0;
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    if (rows.size() == plan.getLimit()) return RawQueryResult.builder().rows(rows).hasMore(true).build();
                    Map<Long, Object> row = new LinkedHashMap<>();
                    for (int index = 0; index < plan.getSelectedFields().size(); index++) {
                        Object value = readCell(results, index + 1);
                        // 按最坏 JSON 转义和稳定字段编码开销计费，避免原始字节少而响应膨胀。
                        resultBytes += value == null ? 128 : 128 + 6L * value.toString().length();
                        if (resultBytes > 8 * 1024 * 1024) throw new ServiceException(DataSourceErrorCode.QUERY_TOO_LARGE);
                        row.put(plan.getSelectedFields().get(index).getFieldId(), value);
                    }
                    rows.add(row);
                }
            }
            return RawQueryResult.builder().rows(rows).hasMore(false).build();
        }
    }

    private static String quote(String identifier) {
        if (identifier == null || identifier.isBlank() || identifier.length() > 64
            || identifier.chars().anyMatch(value -> Character.isISOControl(value) || value == '`')
            || identifier.contains(";") || identifier.contains("--") || identifier.contains("/*")) throw invalid();
        return "`" + identifier + "`";
    }

    private static boolean isText(int type) {
        return type == java.sql.Types.VARCHAR || type == java.sql.Types.CHAR || type == java.sql.Types.LONGVARCHAR;
    }

    private static Object readCell(ResultSet results, int index) throws SQLException {
        int type = results.getMetaData().getColumnType(index);
        if (isText(type) || type == java.sql.Types.CLOB) {
            try (var reader = results.getCharacterStream(index)) {
                if (reader == null) return null;
                var text = new StringBuilder();
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    if (text.length() + count > 1024 * 1024) throw new ServiceException(DataSourceErrorCode.QUERY_TOO_LARGE);
                    text.append(buffer, 0, count);
                }
                String value = text.toString();
                if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 1024 * 1024)
                    throw new ServiceException(DataSourceErrorCode.QUERY_TOO_LARGE);
                return value;
            } catch (java.io.IOException ignored) {
                throw new ServiceException(DataSourceErrorCode.QUERY_FAILED);
            }
        }
        return switch (type) {
            case java.sql.Types.TIMESTAMP -> results.getObject(index, java.time.LocalDateTime.class);
            case java.sql.Types.DATE -> {
                var date = results.getDate(index);
                yield date == null ? null : date.toLocalDate().atStartOfDay();
            }
            case java.sql.Types.BIGINT, java.sql.Types.INTEGER, java.sql.Types.SMALLINT, java.sql.Types.TINYINT,
                 java.sql.Types.DECIMAL, java.sql.Types.NUMERIC, java.sql.Types.BOOLEAN, java.sql.Types.BIT -> results.getObject(index);
            default -> throw invalid();
        };
    }

    private static Object typed(Object value, int type) {
        if (value == null) throw invalid();
        try {
            if (isText(type)) {
                if (!(value instanceof String text) || text.length() > 4096) throw invalid();
                return text;
            }
            return switch (type) {
                case java.sql.Types.BIGINT, java.sql.Types.INTEGER, java.sql.Types.SMALLINT, java.sql.Types.TINYINT ->
                    new java.math.BigDecimal(value.toString()).longValueExact();
                case java.sql.Types.DECIMAL, java.sql.Types.NUMERIC -> new java.math.BigDecimal(value.toString());
                case java.sql.Types.BOOLEAN, java.sql.Types.BIT -> {
                    if (!(value instanceof Boolean)) throw invalid();
                    yield value;
                }
                case java.sql.Types.TIMESTAMP -> java.time.OffsetDateTime.parse(value.toString())
                    .atZoneSameInstant(java.time.ZoneId.of("Asia/Shanghai")).toLocalDateTime();
                case java.sql.Types.DATE -> java.time.LocalDate.parse(value.toString());
                default -> throw invalid();
            };
        } catch (IllegalArgumentException | ArithmeticException | java.time.DateTimeException ignored) {
            throw invalid();
        }
    }

    private record Binding(Object value, int type) { }

    private static ServiceException invalid() { return new ServiceException(DataSourceErrorCode.QUERY_INVALID); }
    private static void forbidden() { throw new ServiceException(DataSourceErrorCode.QUERY_FORBIDDEN); }

    private static boolean timedOut(Throwable failure) {
        for (int depth = 0; failure != null && depth < 10; depth++, failure = failure.getCause()) {
            if (failure instanceof java.sql.SQLTimeoutException || failure instanceof java.net.SocketTimeoutException) return true;
        }
        return false;
    }

    private static java.util.concurrent.ScheduledThreadPoolExecutor deadlineExecutor() {
        var executor = new java.util.concurrent.ScheduledThreadPoolExecutor(2, task -> {
            Thread thread = new Thread(task, "mysql-query-deadline");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }
}
