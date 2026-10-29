package com.xfwang.govdata.query.application.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xfwang.govdata.api.query.AuthorizedQuery;
import com.xfwang.govdata.api.query.AuthorizedQueryGateway;
import com.xfwang.govdata.api.query.QueryColumn;
import com.xfwang.govdata.api.query.QueryResult;
import com.xfwang.govdata.api.query.QueryAuthorization;
import com.xfwang.govdata.api.query.spi.QueryExecutionAdapter;
import com.xfwang.govdata.api.query.spi.QueryResourceSchemaProvider;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.api.mcp.enums.McpRiskLevel;
import com.xfwang.platform.api.mcp.model.McpCallResponse;
import com.xfwang.platform.api.mcp.model.McpModuleDefinition;
import com.xfwang.platform.api.mcp.model.McpToolDefinition;
import com.xfwang.platform.api.mcp.service.McpToolContributor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据查询网关领域 MCP 工具贡献者。
 *
 * <p>对接 {@link AuthorizedQueryGateway}，向 AI Agent 提供安全受控的结构化数据查询与数据抽样能力。
 * 全流程强制执行 ABAC 权限校验、行级过滤（RowFilter）、字段白名单与敏感数据动态脱敏（Masking）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryMcpToolContributor implements McpToolContributor {

    private static final McpModuleDefinition MODULE =
        new McpModuleDefinition("DATASOURCE", "数据源管理");

    private final AuthorizedQueryGateway queryGateway;
    private final ObjectMapper objectMapper;
    private final List<QueryResourceSchemaProvider> schemaProviders;
    private final List<QueryExecutionAdapter> executionAdapters;

    @Override
    public McpModuleDefinition module() {
        return MODULE;
    }

    @Override
    public List<McpToolDefinition> tools() {
        if (schemaProviders.isEmpty() || executionAdapters.isEmpty()) {
            log.info("受控查询 MCP Tools 暂不注册：尚未提供查询 Schema Provider 或执行 Adapter");
            return List.of();
        }
        return List.of(
            McpToolDefinition.builder()
                .name("datasource.query_dataset")
                .moduleCode(MODULE.code())
                .description("对已授权数据资源执行结构化只读数据查询。强制执行 ABAC 行级隔离与敏感信息自动脱敏，单次硬限制最多 50 条，严禁注入 Raw SQL。")
                .inputSchema(Map.of(
                    "type", "object",
                    "additionalProperties", false,
                    "required", List.of("datasource_id", "resource_id", "field_ids"),
                    "properties", Map.of(
                        "datasource_id", Map.of("type", "string", "description", "目标数据源ID"),
                        "resource_id", Map.of("type", "string", "description", "目标授权资源ID"),
                        "field_ids", Map.of("type", "array", "items", Map.of("type", "string"), "description", "需要查询投影的字段ID列表（雪花ID字符串）"),
                        "limit", Map.of("type", "integer", "description", "返回行数上限(默认20，系统最大硬限制50)"),
                        "offset", Map.of("type", "integer", "description", "偏移量(默认0)"),
                        "purpose", Map.of("type", "string", "description", "查询用途(可选，默认 DATA_QUERY)")
                    )
                ))
                .riskLevel(McpRiskLevel.MEDIUM)
                .build(),

            McpToolDefinition.builder()
                .name("datasource.sample_dataset_records")
                .moduleCode(MODULE.code())
                .description("对指定数据资源进行只读抽样，按授权规则过滤和脱敏，返回最多 5 条样例数据")
                .inputSchema(Map.of(
                    "type", "object",
                    "additionalProperties", false,
                    "required", List.of("datasource_id", "resource_id", "field_ids"),
                    "properties", Map.of(
                        "datasource_id", Map.of("type", "string", "description", "目标数据源ID"),
                        "resource_id", Map.of("type", "string", "description", "目标授权资源ID"),
                        "field_ids", Map.of("type", "array", "items", Map.of("type", "string"), "description", "需要查询投影的字段ID列表（雪花ID字符串）")
                    )
                ))
                .riskLevel(McpRiskLevel.LOW)
                .build()
        );
    }

    @Override
    public McpCallResponse execute(String toolName, Map<String, Object> arguments, SubjectContext subject) {
        if (subject == null || arguments == null) return McpCallResponse.error("缺少认证主体或工具参数");
        String previousTrace = MDC.get("traceId");
        if (previousTrace == null) MDC.put("traceId", java.util.UUID.randomUUID().toString().replace("-", ""));
        log.info("执行受控查询 MCP 工具: {}, 主体: [Agent: {}, User: {}]",
            toolName, subject.getPrincipalId(), subject.getUserId());

        try {
            return switch (toolName) {
                case "datasource.query_dataset" -> executeQuery(arguments, subject, false);
                case "datasource.sample_dataset_records" -> executeQuery(arguments, subject, true);
                default -> McpCallResponse.error("不支持的查询工具: " + toolName);
            };
        } catch (Exception e) {
            log.warn("查询 MCP 工具执行失败，tool={}, traceId={}", toolName, MDC.get("traceId"));
            String traceId = MDC.get("traceId");
            return McpCallResponse.error(traceId == null || traceId.isBlank()
                ? "查询失败，请联系管理员" : "查询失败，traceId=" + traceId);
        } finally {
            if (previousTrace == null) MDC.remove("traceId");
            else MDC.put("traceId", previousTrace);
        }
    }

    @SuppressWarnings("unchecked")
    private McpCallResponse executeQuery(Map<String, Object> arguments, SubjectContext subject, boolean isSample) {
        if (!java.util.Set.of("datasource_id", "resource_id", "field_ids", "limit", "offset", "purpose")
            .containsAll(arguments.keySet())) return McpCallResponse.error("查询工具不接受 SQL 或未知参数");
        Long datasourceId = parseLongSafe(arguments.get("datasource_id"));
        Long resourceId = parseLongSafe(arguments.get("resource_id"));

        if (datasourceId == null || resourceId == null) {
            return McpCallResponse.error("缺少必填参数 datasource_id 或 resource_id");
        }

        List<Long> fieldIds = new ArrayList<>();
        Object fieldsObj = arguments.get("field_ids");
        if (fieldsObj instanceof List<?> list) {
            for (Object item : list) {
                Long fid = parseLongSafe(item);
                if (fid == null || fid <= 0) return McpCallResponse.error("field_ids 包含无效字段ID");
                fieldIds.add(fid);
            }
        }

        if (fieldIds.isEmpty() || fieldIds.size() > 100) {
            return McpCallResponse.error("field_ids 不能为空，必须指定至少一个待查询字段ID");
        }

        int limit = isSample ? 5 : 20;
        if (arguments.get("limit") instanceof Number num) {
            limit = Math.min(Math.max(num.intValue(), 1), isSample ? 5 : 50);
        }

        int offset = 0;
        if (arguments.get("offset") instanceof Number num) {
            offset = Math.max(num.intValue(), 0);
        }

        String purpose = arguments.get("purpose") != null
            ? String.valueOf(arguments.get("purpose"))
            : QueryAuthorization.PURPOSE;

        AuthorizedQuery query = AuthorizedQuery.builder()
            .requestId("mcp_" + System.currentTimeMillis())
            .datasourceId(datasourceId)
            .resourceId(resourceId)
            .fieldIds(fieldIds)
            .offset(offset)
            .limit(limit)
            .timeoutMs(10000)
            .purpose(purpose)
            .build();

        QueryResult result = queryGateway.execute(query);

        // 字段映射表: fieldId -> fieldCode
        Map<Long, String> fieldNameMap = result.getColumns() != null
            ? result.getColumns().stream().collect(Collectors.toMap(QueryColumn::getFieldId, c -> c.getFieldCode() != null ? c.getFieldCode() : String.valueOf(c.getFieldId()), (a, b) -> a))
            : Map.of();

        // 转换每行数据为可读 JSON Map
        List<Map<String, Object>> formattedRows = new ArrayList<>();
        if (result.getRows() != null) {
            for (Map<Long, Object> row : result.getRows()) {
                Map<String, Object> namedRow = new LinkedHashMap<>();
                row.forEach((k, v) -> namedRow.put(fieldNameMap.getOrDefault(k, String.valueOf(k)), v));
                formattedRows.add(namedRow);
            }
        }

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("datasourceId", String.valueOf(datasourceId));
        responseMap.put("resourceId", String.valueOf(resourceId));
        responseMap.put("returnedRows", result.getReturnedRows());
        responseMap.put("hasMore", result.getHasMore());
        responseMap.put("columns", result.getColumns());
        responseMap.put("records", formattedRows);

        try {
            return McpCallResponse.success(objectMapper.writeValueAsString(responseMap));
        } catch (Exception e) {
            log.error("序列化 MCP 查询结果失败", e);
            return McpCallResponse.error("序列化查询结果失败，请联系管理员");
        }
    }

    private Long parseLongSafe(Object obj) {
        if (obj == null) return null;
        try {
            return Long.parseLong(String.valueOf(obj).trim());
        } catch (Exception e) {
            return null;
        }
    }
}
