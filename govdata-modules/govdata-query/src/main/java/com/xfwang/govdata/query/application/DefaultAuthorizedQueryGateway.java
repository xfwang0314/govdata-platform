package com.xfwang.govdata.query.application;

import com.xfwang.govdata.query.domain.exception.QueryErrorCode;
import com.xfwang.govdata.api.query.AuthorizedQuery;
import com.xfwang.govdata.api.query.AuthorizedQueryGateway;
import com.xfwang.govdata.api.query.QueryColumn;
import com.xfwang.govdata.api.query.QueryDirection;
import com.xfwang.govdata.api.query.QueryFilter;
import com.xfwang.govdata.api.query.QueryOperator;
import com.xfwang.govdata.api.query.QueryResult;
import com.xfwang.govdata.api.query.QuerySort;
import com.xfwang.govdata.api.query.spi.AuthorizedExecutionPlan;
import com.xfwang.govdata.api.query.spi.QueryExecutionAdapter;
import com.xfwang.govdata.api.query.spi.QueryFieldSchema;
import com.xfwang.govdata.api.query.spi.QueryResourceSchema;
import com.xfwang.govdata.api.query.spi.QueryResourceSchemaProvider;
import com.xfwang.govdata.api.query.spi.RawQueryResult;
import com.xfwang.platform.api.auth.enums.ConditionOperator;
import com.xfwang.platform.api.auth.model.AccessDecision;
import com.xfwang.platform.api.auth.model.AccessRequest;
import com.xfwang.platform.api.auth.model.AuthorizationObligation;
import com.xfwang.platform.api.auth.model.RowFilter;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.api.auth.service.AuthorizationContextAccessor;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 授权查询网关默认实现。
 *
 * <p>调用方只提交稳定 ID 和结构化条件；本实现统一执行授权上下文、元数据字段、查询限额、
 * Obligation 和结果脱敏。</p>
 */
@Service
@RequiredArgsConstructor
public class DefaultAuthorizedQueryGateway implements AuthorizedQueryGateway {

    private static final int PLATFORM_MAX_FIELDS = 100;
    private static final int PLATFORM_MAX_FILTERS = 50;
    private static final int PLATFORM_MAX_SORTS = 5;
    private static final int PLATFORM_MAX_ROWS = 1_000;
    private static final int PLATFORM_MAX_TIMEOUT_MS = 30_000;

    private final AuthorizationContextAccessor authorizationContextAccessor;
    private final List<QueryResourceSchemaProvider> schemaProviders;
    private final List<QueryExecutionAdapter> executionAdapters;

    @Override
    public QueryResult execute(AuthorizedQuery query) {
        validateRequest(query);
        AccessRequest accessRequest = authorizationContextAccessor.currentRequest()
            .orElseThrow(() -> error(QueryErrorCode.AUTHORIZATION_REQUIRED, "查询必须从 @ResourceAuthorize 入口调用"));
        AccessDecision decision = authorizationContextAccessor.currentDecision()
            .filter(AccessDecision::isAllowed)
            .orElseThrow(() -> error(QueryErrorCode.AUTHORIZATION_REQUIRED, "当前授权决策不是 PERMIT"));
        if (!Objects.equals(query.getResourceId(), accessRequest.getResourceId())) {
            throw error(QueryErrorCode.AUTHORIZATION_REQUIRED, "查询资源与授权资源不一致");
        }
        if (StringUtils.isNotBlank(accessRequest.getEnvironment() == null
            ? null : accessRequest.getEnvironment().getPurpose())
            && !StringUtils.equals(query.getPurpose(), accessRequest.getEnvironment().getPurpose())) {
            throw error(QueryErrorCode.AUTHORIZATION_REQUIRED, "查询用途与授权用途不一致");
        }

        QueryResourceSchema schema = schemaProviders.stream()
            .map(provider -> provider.findByResourceId(query.getResourceId()))
            .flatMap(java.util.Optional::stream)
            .findFirst()
            .orElseThrow(() -> error(QueryErrorCode.SCHEMA_NOT_FOUND, "未找到授权资源对应的查询元数据"));
        if (!Objects.equals(query.getDatasourceId(), schema.getDatasourceId())) {
            throw error(QueryErrorCode.INVALID_REQUEST, "数据源与资源元数据不一致");
        }

        Map<Long, QueryFieldSchema> fieldsById = safe(schema.getFields()).stream()
            .collect(Collectors.toMap(QueryFieldSchema::getFieldId, Function.identity(), (left, right) -> left));
        List<QueryFieldSchema> selectedFields = resolveSelectedFields(query.getFieldIds(), fieldsById);
        AuthorizationObligation obligation = decision.getObligation() == null
            ? AuthorizationObligation.builder().build()
            : decision.getObligation();
        enforceColumnScope(selectedFields, obligation.getAllowedColumns());

        List<QueryFilter> filters = new ArrayList<>();
        List<QueryFilter> userFilters = validateUserFilters(query.getFilters(), fieldsById);
        enforcePredicateScope(userFilters.stream().map(filter -> fieldsById.get(filter.getFieldId())).toList(), obligation);
        filters.addAll(userFilters);
        filters.addAll(compileObligationFilters(obligation.getRowFilters(), schema, accessRequest));
        List<QuerySort> sorts = validateSorts(query.getSorts(), fieldsById);
        enforcePredicateScope(sorts.stream().map(sort -> fieldsById.get(sort.getFieldId())).toList(), obligation);
        int limit = effectiveLimit(query.getLimit(), obligation.getMaxRows());
        int timeoutMs = Math.min(query.getTimeoutMs(), PLATFORM_MAX_TIMEOUT_MS);

        AuthorizedExecutionPlan plan = AuthorizedExecutionPlan.builder()
            .requestId(query.getRequestId())
            .resource(schema)
            .selectedFields(selectedFields)
            .filters(filters)
            .sorts(sorts)
            .offset(query.getOffset())
            .limit(limit)
            .timeoutMs(timeoutMs)
            .build();
        QueryExecutionAdapter adapter = executionAdapters.stream()
            .filter(value -> value.supports(schema.getConnectorCode()))
            .findFirst()
            .orElseThrow(() -> error(QueryErrorCode.ADAPTER_NOT_FOUND,
                "未找到 Connector " + schema.getConnectorCode() + " 的查询适配器"));
        RawQueryResult raw = adapter.execute(plan);
        return protectResult(raw, selectedFields, obligation, limit);
    }

    private void validateRequest(AuthorizedQuery query) {
        if (query == null || query.getDatasourceId() == null || query.getResourceId() == null
            || query.getFieldIds() == null || query.getFieldIds().isEmpty()
            || query.getFieldIds().size() > PLATFORM_MAX_FIELDS) {
            throw error(QueryErrorCode.INVALID_REQUEST, "数据源、资源和1至100个字段不能为空");
        }
        if (query.getOffset() == null || query.getOffset() < 0 || query.getOffset() > 10000 || query.getLimit() == null || query.getLimit() <= 0
            || query.getTimeoutMs() == null || query.getTimeoutMs() <= 0) {
            throw error(QueryErrorCode.INVALID_REQUEST, "offset、limit或timeoutMs无效");
        }
        if (safe(query.getFilters()).size() > PLATFORM_MAX_FILTERS
            || safe(query.getSorts()).size() > PLATFORM_MAX_SORTS) {
            throw error(QueryErrorCode.INVALID_REQUEST, "过滤条件最多50个，排序最多5个");
        }
    }

    private List<QueryFieldSchema> resolveSelectedFields(List<Long> ids, Map<Long, QueryFieldSchema> fieldsById) {
        Set<Long> unique = new LinkedHashSet<>(ids);
        if (unique.size() != ids.size() || unique.contains(null)) {
            throw error(QueryErrorCode.INVALID_REQUEST, "查询字段不能为空或重复");
        }
        return unique.stream().map(id -> {
            QueryFieldSchema field = fieldsById.get(id);
            if (field == null || !Boolean.TRUE.equals(field.getSelectable())) {
                throw error(QueryErrorCode.INVALID_REQUEST, "查询字段不存在或不可查询: " + id);
            }
            return field;
        }).toList();
    }

    private void enforceColumnScope(List<QueryFieldSchema> fields, Set<String> allowedColumns) {
        if (allowedColumns == null) {
            return;
        }
        boolean forbidden = fields.stream().anyMatch(field -> !allowedColumns.contains(field.getFieldCode())
            && !allowedColumns.contains(field.getAuthorizationAttribute()));
        if (forbidden) {
            throw error(QueryErrorCode.FIELD_FORBIDDEN, "请求包含字段白名单之外的字段");
        }
    }

    private List<QueryFilter> validateUserFilters(List<QueryFilter> values,
                                                   Map<Long, QueryFieldSchema> fieldsById) {
        return safe(values).stream().map(filter -> {
            if (filter == null || filter.getFieldId() == null || filter.getOperator() == null
                || !fieldsById.containsKey(filter.getFieldId())) {
                throw error(QueryErrorCode.INVALID_REQUEST, "过滤字段或操作符无效");
            }
            validateFilterValues(filter.getOperator(), filter.getValues());
            return filter;
        }).toList();
    }

    private void enforcePredicateScope(List<QueryFieldSchema> fields, AuthorizationObligation obligation) {
        enforceColumnScope(fields, obligation.getAllowedColumns());
        if (fields.stream().anyMatch(field -> {
            String rule = maskingRule(field, obligation.getMaskingRules());
            return StringUtils.isNotBlank(rule) && !"NONE".equalsIgnoreCase(rule.trim());
        })) {
            throw error(QueryErrorCode.FIELD_FORBIDDEN, "不能通过过滤或排序探测已脱敏字段");
        }
    }

    private List<QuerySort> validateSorts(List<QuerySort> values, Map<Long, QueryFieldSchema> fieldsById) {
        return safe(values).stream().map(sort -> {
            if (sort == null || sort.getFieldId() == null || !fieldsById.containsKey(sort.getFieldId())) {
                throw error(QueryErrorCode.INVALID_REQUEST, "排序字段无效");
            }
            if (sort.getDirection() == null) {
                sort.setDirection(QueryDirection.ASC);
            }
            return sort;
        }).toList();
    }

    private List<QueryFilter> compileObligationFilters(List<RowFilter> values,
                                                        QueryResourceSchema schema,
                                                        AccessRequest request) {
        Map<String, QueryFieldSchema> byAttribute = safe(schema.getFields()).stream()
            .filter(field -> StringUtils.isNotBlank(field.getAuthorizationAttribute()))
            .collect(Collectors.toMap(QueryFieldSchema::getAuthorizationAttribute,
                Function.identity(), (left, right) -> left));
        return safe(values).stream().map(value -> {
            QueryFieldSchema field = byAttribute.get(value.getAttribute());
            if (field == null) {
                throw error(QueryErrorCode.AUTHORIZATION_REQUIRED,
                    "授权行条件引用了未注册属性: " + value.getAttribute());
            }
            QueryOperator operator = mapOperator(value.getOperator());
            List<Object> resolvedValues = safe(value.getValues()).stream()
                .map(item -> resolveObligationValue(item, request)).toList();
            validateFilterValues(operator, resolvedValues);
            return QueryFilter.builder().fieldId(field.getFieldId()).operator(operator)
                .values(resolvedValues).build();
        }).toList();
    }

    private QueryOperator mapOperator(ConditionOperator operator) {
        if (operator == null) {
            throw error(QueryErrorCode.AUTHORIZATION_REQUIRED, "授权行条件缺少操作符");
        }
        return switch (operator) {
            case EQ -> QueryOperator.EQ;
            case NE -> QueryOperator.NE;
            case GT -> QueryOperator.GT;
            case GTE -> QueryOperator.GTE;
            case LT -> QueryOperator.LT;
            case LTE -> QueryOperator.LTE;
            case IN -> QueryOperator.IN;
            case NOT_IN -> QueryOperator.NOT_IN;
            case EXISTS -> QueryOperator.IS_NOT_NULL;
            case NOT_EXISTS -> QueryOperator.IS_NULL;
            case CONTAINS, BETWEEN, INTERSECTS, STARTS_WITH -> throw error(
                QueryErrorCode.AUTHORIZATION_REQUIRED,
                "查询网关暂不支持授权行条件操作符: " + operator.code());
        };
    }

    private Object resolveObligationValue(Object value, AccessRequest request) {
        if (!(value instanceof String text) || !text.startsWith("subject.")) {
            return value;
        }
        SubjectContext subject = request.getSubject();
        if (subject == null) {
            throw error(QueryErrorCode.AUTHORIZATION_REQUIRED, "授权上下文缺少主体属性");
        }
        String attribute = text.substring("subject.".length());
        Object resolved = switch (attribute) {
            case "tenantId" -> subject.getTenantId();
            case "userId" -> subject.getUserId();
            case "deptId" -> subject.getDeptId();
            case "roleIds" -> subject.getRoleIds();
            case "roleKeys" -> subject.getRoleKeys();
            case "deptPathIds" -> subject.getDeptPathIds();
            default -> subject.getAttributes() == null ? null : subject.getAttributes().get(attribute);
        };
        if (resolved == null) {
            throw error(QueryErrorCode.AUTHORIZATION_REQUIRED, "主体授权属性不存在: " + attribute);
        }
        return resolved;
    }

    private void validateFilterValues(QueryOperator operator, List<Object> values) {
        List<Object> safeValues = safe(values);
        boolean noValue = operator == QueryOperator.IS_NULL || operator == QueryOperator.IS_NOT_NULL;
        boolean multiValue = operator == QueryOperator.IN || operator == QueryOperator.NOT_IN;
        if ((!noValue && safeValues.isEmpty()) || (noValue && !safeValues.isEmpty())
            || (!multiValue && !noValue && safeValues.size() != 1)) {
            throw error(QueryErrorCode.INVALID_REQUEST, "过滤值数量与操作符不匹配");
        }
        if (safeValues.stream().anyMatch(value -> !isScalarOrScalarCollection(value))) {
            throw error(QueryErrorCode.INVALID_REQUEST, "过滤值只能使用字符串、数字、布尔或时间文本");
        }
    }

    private boolean isScalarOrScalarCollection(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return true;
        }
        return value instanceof Collection<?> collection
            && collection.stream().allMatch(this::isScalarOrScalarCollection);
    }

    private int effectiveLimit(Integer requested, Integer obligationMaxRows) {
        int result = Math.min(requested, PLATFORM_MAX_ROWS);
        if (obligationMaxRows != null && obligationMaxRows <= 0) {
            throw error(QueryErrorCode.AUTHORIZATION_REQUIRED, "授权最大返回行数必须大于0");
        }
        return obligationMaxRows == null ? result : Math.min(result, obligationMaxRows);
    }

    private QueryResult protectResult(RawQueryResult raw, List<QueryFieldSchema> fields,
                                      AuthorizationObligation obligation, int limit) {
        List<Map<Long, Object>> sourceRows = raw == null ? List.of() : safe(raw.getRows());
        boolean truncated = sourceRows.size() > limit;
        List<Map<Long, Object>> rows = sourceRows.stream().limit(limit).map(source -> {
            Map<Long, Object> protectedRow = new LinkedHashMap<>();
            fields.forEach(field -> protectedRow.put(field.getFieldId(),
                mask(source.get(field.getFieldId()), maskingRule(field, obligation.getMaskingRules()))));
            return protectedRow;
        }).toList();
        List<QueryColumn> columns = fields.stream().map(field -> QueryColumn.builder()
            .fieldId(field.getFieldId())
            .fieldCode(field.getFieldCode())
            .displayName(field.getDisplayName())
            .masked(StringUtils.isNotBlank(maskingRule(field, obligation.getMaskingRules())))
            .build()).toList();
        return QueryResult.builder()
            .columns(columns)
            .rows(rows)
            .returnedRows(rows.size())
            .hasMore(truncated || raw != null && Boolean.TRUE.equals(raw.getHasMore()))
            .build();
    }

    private String maskingRule(QueryFieldSchema field, Map<String, String> rules) {
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        String value = rules.get(field.getAuthorizationAttribute());
        return value == null ? rules.get(field.getFieldCode()) : value;
    }

    private Object mask(Object value, String rule) {
        if (value == null || StringUtils.isBlank(rule)) {
            return value;
        }
        String text = value.toString();
        return switch (rule.trim().toUpperCase(Locale.ROOT)) {
            case "MOBILE" -> text.length() < 7 ? "****" : text.substring(0, 3) + "****" + text.substring(text.length() - 4);
            case "ID_CARD" -> text.length() < 10 ? "****" : text.substring(0, 6) + "********" + text.substring(text.length() - 4);
            case "EMAIL" -> maskEmail(text);
            case "NAME" -> text.isEmpty() ? text : text.substring(0, 1) + "**";
            case "NONE" -> value;
            case "FULL" -> "****";
            default -> "****";
        };
    }

    private String maskEmail(String text) {
        int at = text.indexOf('@');
        return at <= 0 ? "****" : text.substring(0, 1) + "***" + text.substring(at);
    }

    private ServiceException error(QueryErrorCode code, String message) {
        return new ServiceException(code, message);
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
