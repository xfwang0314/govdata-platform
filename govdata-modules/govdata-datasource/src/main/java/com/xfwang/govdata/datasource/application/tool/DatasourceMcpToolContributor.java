package com.xfwang.govdata.datasource.application.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xfwang.govdata.datasource.application.metadata.DataSourceMetadataInspectService;
import com.xfwang.govdata.datasource.application.metadata.view.DatabaseView;
import com.xfwang.govdata.datasource.application.metadata.view.TableSchemaView;
import com.xfwang.govdata.datasource.application.metadata.view.TableView;
import com.xfwang.govdata.datasource.domain.entity.DataSourceInstance;
import com.xfwang.govdata.datasource.infrastructure.persistence.mapper.DataSourceInstanceMapper;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.api.auth.model.AuthorizedResourceQuery;
import com.xfwang.platform.api.auth.model.AuthorizedResourceScope;
import com.xfwang.platform.api.auth.service.AuthorizedResourceService;
import com.xfwang.platform.api.auth.service.ResourceRegistry;
import com.xfwang.govdata.datasource.domain.authorization.DataSourceAuthorizationCodes;
import com.xfwang.platform.api.mcp.enums.McpRiskLevel;
import com.xfwang.platform.api.mcp.model.McpCallResponse;
import com.xfwang.platform.api.mcp.model.McpModuleDefinition;
import com.xfwang.platform.api.mcp.model.McpToolDefinition;
import com.xfwang.platform.api.mcp.service.McpToolContributor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据源领域真实 MCP 工具贡献者。
 *
 * <p>直接对接 {@link DataSourceInstanceMapper} 与 {@link DataSourceMetadataInspectService}，
 * 向 AI Agent 提供真实的数据源列表探测、库清单、表清单及字段结构字典探测能力。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatasourceMcpToolContributor implements McpToolContributor {

    private static final McpModuleDefinition MODULE =
        new McpModuleDefinition("DATASOURCE", "数据源管理");

    private final DataSourceInstanceMapper instanceMapper;
    private final DataSourceMetadataInspectService metadataInspectService;
    private final ObjectMapper objectMapper;
    private final AuthorizedResourceService authorizedResourceService;
    private final ResourceRegistry resourceRegistry;

    @Override
    public McpModuleDefinition module() {
        return MODULE;
    }

    @Override
    public List<McpToolDefinition> tools() {
        return List.of(
            McpToolDefinition.builder()
                .name("datasource.list_datasources")
                .moduleCode(MODULE.code())
                .description("查询当前租户下所有已启用的真实数据源清单（包含数据源ID、名称、Connector驱动类型、安全密级与环境）")
                .inputSchema(Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "keyword", Map.of("type", "string", "description", "数据源名称或编码过滤关键词(可选)")
                    )
                ))
                .riskLevel(McpRiskLevel.LOW)
                .build(),

            McpToolDefinition.builder()
                .name("datasource.list_databases")
                .moduleCode(MODULE.code())
                .description("连接目标真实数据源，实时探测并返回该数据源实例下的所有数据库 / Schema 清单")
                .inputSchema(Map.of(
                    "type", "object",
                    "required", List.of("datasource_id"),
                    "properties", Map.of(
                        "datasource_id", Map.of("type", "string", "description", "目标数据源ID")
                    )
                ))
                .riskLevel(McpRiskLevel.LOW)
                .build(),

            McpToolDefinition.builder()
                .name("datasource.list_tables")
                .moduleCode(MODULE.code())
                .description("连接目标真实数据源，探测指定数据库/Schema下的所有物理数据表清单（包含表名、表注释与表类型）")
                .inputSchema(Map.of(
                    "type", "object",
                    "required", List.of("datasource_id"),
                    "properties", Map.of(
                        "datasource_id", Map.of("type", "string", "description", "目标数据源ID"),
                        "database_name", Map.of("type", "string", "description", "指定数据库或Schema名(可选)"),
                        "table_pattern", Map.of("type", "string", "description", "表名模糊匹配关键词(可选)"),
                        "limit", Map.of("type", "integer", "description", "最大返回表数量(默认100，最大500)")
                    )
                ))
                .riskLevel(McpRiskLevel.LOW)
                .build(),

            McpToolDefinition.builder()
                .name("datasource.get_table_schema")
                .moduleCode(MODULE.code())
                .description("连接目标真实数据源，深度探查指定表的完整字段元数据字典（包含字段名、物理类型、中文注释、是否主键及是否可空）")
                .inputSchema(Map.of(
                    "type", "object",
                    "required", List.of("datasource_id", "table_name"),
                    "properties", Map.of(
                        "datasource_id", Map.of("type", "string", "description", "目标数据源ID"),
                        "database_name", Map.of("type", "string", "description", "指定数据库或Schema名(可选)"),
                        "table_name", Map.of("type", "string", "description", "目标物理表名")
                    )
                ))
                .riskLevel(McpRiskLevel.LOW)
                .build()
        );
    }

    @Override
    public McpCallResponse execute(String toolName, Map<String, Object> arguments, SubjectContext subject) {
        log.info("执行真实数据源 MCP 工具: {}, 主体: [Agent: {}, User: {}]",
            toolName, subject.getPrincipalId(), subject.getUserId());

        try {
            return switch (toolName) {
                case "datasource.list_datasources" -> executeListDatasources(arguments, subject);
                case "datasource.list_databases" -> executeListDatabases(arguments, subject);
                case "datasource.list_tables" -> executeListTables(arguments, subject);
                case "datasource.get_table_schema" -> executeGetTableSchema(arguments, subject);
                default -> McpCallResponse.error("不支持的数据源工具: " + toolName);
            };
        } catch (Exception e) {
            log.error("执行数据源 MCP 工具 [{}] 异常: {}", toolName, e.getMessage(), e);
            return McpCallResponse.error(safeErrorMessage("执行工具失败"));
        }
    }

    private McpCallResponse executeListDatasources(Map<String, Object> arguments, SubjectContext subject) {
        String keyword = arguments.get("keyword") != null ? String.valueOf(arguments.get("keyword")).trim() : null;

        LambdaQueryWrapper<DataSourceInstance> wrapper = new LambdaQueryWrapper<DataSourceInstance>()
            .eq(DataSourceInstance::getStatus, "ACTIVE")
            .like(keyword != null && !keyword.isEmpty(), DataSourceInstance::getDatasourceName, keyword)
            .orderByAsc(DataSourceInstance::getDatasourceName);

        AuthorizedResourceScope scope = authorizedResourceService.resolveScope(
            AuthorizedResourceQuery.builder()
                .resourceType(DataSourceAuthorizationCodes.RESOURCE_TYPE)
                .action(DataSourceAuthorizationCodes.Action.METADATA_INSPECT)
                .purpose(DataSourceAuthorizationCodes.Purpose.METADATA_INSPECTION)
                .build());
        if (!scope.unrestricted()) {
            List<Long> datasourceIds = scope.resourceIds().stream()
                .map(resourceRegistry::findById)
                .flatMap(java.util.Optional::stream)
                .map(com.xfwang.platform.api.auth.model.ResourceDescriptor::getBusinessId)
                .map(this::parseDatasourceId)
                .filter(java.util.Objects::nonNull)
                .toList();
            if (datasourceIds.isEmpty()) {
                return toJsonSuccess(Map.of("total", 0, "datasources", List.of()));
            }
            wrapper.in(DataSourceInstance::getDatasourceId, datasourceIds);
        }

        List<DataSourceInstance> instances = instanceMapper.selectList(wrapper);

        List<Map<String, Object>> views = instances.stream()
            .map(ds -> {
                Map<String, Object> map = new HashMap<>();
                map.put("datasourceId", String.valueOf(ds.getDatasourceId()));
                map.put("datasourceName", ds.getDatasourceName());
                map.put("datasourceCode", ds.getDatasourceCode());
                map.put("connectorCode", ds.getConnectorCode());
                map.put("connectorVersion", ds.getConnectorVersion());
                map.put("environment", ds.getEnvironment());
                map.put("securityLevel", ds.getSecurityLevel());
                map.put("status", ds.getStatus());
                return map;
            })
            .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("total", views.size());
        result.put("datasources", views);

        return toJsonSuccess(result);
    }

    private McpCallResponse executeListDatabases(Map<String, Object> arguments, SubjectContext subject) {
        Long datasourceId = parseDatasourceId(arguments.get("datasource_id"));
        if (datasourceId == null) {
            return McpCallResponse.error("缺少必填参数 datasource_id");
        }

        List<DatabaseView> databases = metadataInspectService.listDatabases(datasourceId);

        Map<String, Object> result = new HashMap<>();
        result.put("datasourceId", String.valueOf(datasourceId));
        result.put("databaseCount", databases.size());
        result.put("databases", databases.stream().map(DatabaseView::getDatabaseName).toList());

        return toJsonSuccess(result);
    }

    private McpCallResponse executeListTables(Map<String, Object> arguments, SubjectContext subject) {
        Long datasourceId = parseDatasourceId(arguments.get("datasource_id"));
        if (datasourceId == null) {
            return McpCallResponse.error("缺少必填参数 datasource_id");
        }

        String dbName = arguments.get("database_name") != null ? String.valueOf(arguments.get("database_name")) : null;
        String pattern = arguments.get("table_pattern") != null ? String.valueOf(arguments.get("table_pattern")) : null;
        int limit = 100;
        if (arguments.get("limit") instanceof Number num) {
            limit = Math.min(Math.max(num.intValue(), 1), 500);
        }

        List<TableView> tables = metadataInspectService.listTables(datasourceId, dbName, dbName, pattern, null, 0, limit);

        List<Map<String, Object>> tableList = tables.stream()
            .map(t -> {
                Map<String, Object> map = new HashMap<>();
                map.put("tableName", t.getTableName());
                map.put("tableComment", t.getRemarks());
                map.put("tableType", t.getTableType());
                map.put("catalog", t.getCatalog());
                map.put("schema", t.getSchema());
                return map;
            })
            .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("datasourceId", String.valueOf(datasourceId));
        result.put("tableCount", tableList.size());
        result.put("tables", tableList);

        return toJsonSuccess(result);
    }

    private McpCallResponse executeGetTableSchema(Map<String, Object> arguments, SubjectContext subject) {
        Long datasourceId = parseDatasourceId(arguments.get("datasource_id"));
        String tableName = arguments.get("table_name") != null ? String.valueOf(arguments.get("table_name")) : null;

        if (datasourceId == null || tableName == null || tableName.isBlank()) {
            return McpCallResponse.error("缺少必填参数 datasource_id 或 table_name");
        }

        String dbName = arguments.get("database_name") != null ? String.valueOf(arguments.get("database_name")) : null;

        List<TableSchemaView> schemaViews = metadataInspectService.inspectColumns(
            datasourceId, dbName, dbName, List.of(tableName.trim())
        );

        if (schemaViews.isEmpty()) {
            return McpCallResponse.error("未找到表 " + tableName + " 的元数据结构");
        }

        TableSchemaView tableSchema = schemaViews.get(0);
        List<Map<String, Object>> columnList = tableSchema.getColumns().stream()
            .map(c -> {
                Map<String, Object> col = new HashMap<>();
                col.put("columnName", c.getColumnName());
                col.put("dataType", c.getDataType());
                col.put("columnSize", c.getColumnSize());
                col.put("nullable", c.isNullable());
                col.put("primaryKey", c.isPrimaryKey());
                col.put("comment", c.getRemarks());
                return col;
            })
            .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("datasourceId", String.valueOf(datasourceId));
        result.put("tableName", tableSchema.getTable() != null ? tableSchema.getTable().getTableName() : tableName);
        result.put("tableComment", tableSchema.getTable() != null ? tableSchema.getTable().getRemarks() : "");
        result.put("columnCount", columnList.size());
        result.put("columns", columnList);

        return toJsonSuccess(result);
    }

    private Long parseDatasourceId(Object obj) {
        if (obj == null) return null;
        try {
            return Long.parseLong(String.valueOf(obj).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private McpCallResponse toJsonSuccess(Object data) {
        try {
            return McpCallResponse.success(objectMapper.writeValueAsString(data));
        } catch (Exception e) {
            log.error("序列化 MCP 数据源工具结果失败", e);
            return McpCallResponse.error(safeErrorMessage("JSON 序列化失败"));
        }
    }

    private String safeErrorMessage(String message) {
        String traceId = MDC.get("traceId");
        return traceId == null || traceId.isBlank()
            ? message + "，请联系管理员" : message + "，traceId=" + traceId;
    }
}
