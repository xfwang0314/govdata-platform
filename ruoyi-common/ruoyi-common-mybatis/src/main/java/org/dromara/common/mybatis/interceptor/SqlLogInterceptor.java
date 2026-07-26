package org.dromara.common.mybatis.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * 开发环境 SQL 日志拦截器。
 *
 * <p>记录 Mapper、规范化 SQL、脱敏参数和耗时，不代理 JDBC 连接。</p>
 */
@Slf4j
@Intercepts({
    @Signature(type = Executor.class, method = "update",
        args = {MappedStatement.class, Object.class}),
    @Signature(type = Executor.class, method = "query",
        args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
    @Signature(type = Executor.class, method = "query",
        args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
            org.apache.ibatis.cache.CacheKey.class, BoundSql.class})
})
public class SqlLogInterceptor implements Interceptor {

    private static final String MASKED = "******";

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs().length > 1 ? invocation.getArgs()[1] : null;
        BoundSql boundSql = invocation.getArgs().length == 6
            ? (BoundSql) invocation.getArgs()[5] : statement.getBoundSql(parameter);
        long start = System.nanoTime();
        try {
            Object result = invocation.proceed();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.info("SQL mapper={} time={}ms affected={} sql={} params={}",
                statement.getId(), elapsedMs, affectedRows(result),
                normalize(boundSql.getSql()), resolveParameters(statement.getConfiguration(), boundSql));
            return result;
        } catch (Throwable throwable) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.error("SQL执行失败 mapper={} time={}ms sql={} params={}",
                statement.getId(), elapsedMs, normalize(boundSql.getSql()),
                resolveParameters(statement.getConfiguration(), boundSql), throwable);
            throw throwable;
        }
    }

    private Map<String, Object> resolveParameters(Configuration configuration, BoundSql boundSql) {
        Map<String, Object> values = new LinkedHashMap<>();
        Object parameterObject = boundSql.getParameterObject();
        MetaObject metaObject = parameterObject == null ? null : configuration.newMetaObject(parameterObject);
        for (ParameterMapping mapping : boundSql.getParameterMappings()) {
            if (mapping.getMode() == ParameterMode.OUT) {
                continue;
            }
            String property = mapping.getProperty();
            Object value = null;
            if (boundSql.hasAdditionalParameter(property)) {
                value = boundSql.getAdditionalParameter(property);
            } else if (parameterObject != null
                && configuration.getTypeHandlerRegistry().hasTypeHandler(parameterObject.getClass())) {
                value = parameterObject;
            } else if (metaObject != null && metaObject.hasGetter(property)) {
                value = metaObject.getValue(property);
            }
            values.put(property, isSensitive(property) ? MASKED : value);
        }
        return values;
    }

    private boolean isSensitive(String property) {
        String name = property.toLowerCase(Locale.ROOT);
        return name.contains("password") || name.contains("passwd") || name.contains("secret")
            || name.contains("token") || name.contains("credential");
    }

    private String normalize(String sql) {
        return sql == null ? "" : sql.replaceAll("\\s+", " ").trim();
    }

    private int affectedRows(Object result) {
        if (result instanceof Number number) {
            return number.intValue();
        }
        if (result instanceof List<?> list) {
            return list.size();
        }
        return -1;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }
}
