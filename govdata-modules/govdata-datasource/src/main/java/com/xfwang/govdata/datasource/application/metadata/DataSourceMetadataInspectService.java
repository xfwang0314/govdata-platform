package com.xfwang.govdata.datasource.application.metadata;

import com.xfwang.govdata.datasource.application.metadata.view.ColumnView;
import com.xfwang.govdata.datasource.application.metadata.view.DatabaseView;
import com.xfwang.govdata.datasource.application.metadata.view.TableSchemaView;
import com.xfwang.govdata.datasource.application.metadata.view.TableView;
import com.xfwang.govdata.api.metadata.CollectedMetadataColumn;
import com.xfwang.govdata.api.metadata.CollectedMetadataNamespace;
import com.xfwang.govdata.api.metadata.CollectedMetadataTable;
import com.xfwang.govdata.api.metadata.MetadataObjectType;
import com.xfwang.govdata.api.metadata.MetadataDiscoveryGateway;
import com.xfwang.govdata.api.metadata.MetadataDiscoveryRequest;
import com.xfwang.govdata.api.metadata.MetadataDiscoveryResult;
import com.xfwang.govdata.api.metadata.MetadataDiscoverySink;
import com.xfwang.govdata.datasource.domain.connector.ConnectorDescriptor;
import com.xfwang.govdata.datasource.domain.entity.DataSourceInstance;
import com.xfwang.govdata.datasource.domain.enums.DataSourceSecretUsage;
import com.xfwang.govdata.datasource.domain.exception.DataSourceErrorCode;
import com.xfwang.govdata.datasource.infrastructure.connector.ConnectorCatalog;
import com.xfwang.govdata.datasource.infrastructure.connector.model.ColumnMeta;
import com.xfwang.govdata.datasource.infrastructure.connector.model.DatabaseMeta;
import com.xfwang.govdata.datasource.infrastructure.connector.model.MetadataInspectRequest;
import com.xfwang.govdata.datasource.infrastructure.connector.model.MetadataInspectResult;
import com.xfwang.govdata.datasource.infrastructure.connector.model.TableMeta;
import com.xfwang.govdata.datasource.infrastructure.connector.model.TableSchema;
import com.xfwang.govdata.datasource.infrastructure.connector.registry.MetadataInspectorRegistry;
import com.xfwang.govdata.datasource.domain.authorization.DataSourceAuthorizationCodes;
import com.xfwang.govdata.datasource.infrastructure.persistence.mapper.DataSourceInstanceMapper;
import com.xfwang.platform.api.auth.annotation.ResourceAuthorize;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.api.auth.service.SubjectProvider;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.tenant.helper.TenantHelper;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 数据源元数据探测应用服务。
 *
 * <p>提供数据库清单、表清单分页查询和批量字段结构探测功能，探测任务提交到隔离线程池执行。</p>
 */
@Service
@RequiredArgsConstructor
public class DataSourceMetadataInspectService implements MetadataDiscoveryGateway {

    private static final int MAX_BATCH_SIZE = 500;
    private static final int MAX_TABLE_LIMIT = 1000;
    private static final long HARD_TIMEOUT_SECONDS = 30;

    private final DataSourceInstanceMapper instanceMapper;
    private final MetadataInspectorRegistry inspectorRegistry;
    private final ConnectorCatalog connectorCatalog;
    private final SubjectProvider subjectProvider;

    @Qualifier("connectionTestExecutor")
    private final Executor connectionTestExecutor;

    /**
     * 查询数据源拥有的数据库 / Schema 清单。
     */
    @ResourceAuthorize(businessId = "#datasourceId",
        sourceModule = DataSourceAuthorizationCodes.MODULE,
        resourceType = DataSourceAuthorizationCodes.RESOURCE_TYPE,
        action = DataSourceAuthorizationCodes.Action.METADATA_INSPECT,
        purpose = DataSourceAuthorizationCodes.Purpose.METADATA_INSPECTION)
    public List<DatabaseView> listDatabases(Long datasourceId) {
        DataSourceInstance instance = requireActive(datasourceId);
        requireCapability(instance, "METADATA_INSPECT");

        MetadataInspectRequest request = buildRequest(instance, null, null, null, null, 0, 0);
        MetadataInspectResult result = executeOnPool(request.getTenantId(), () ->
            inspectorRegistry.require(instance.getConnectorCode(), instance.getConnectorVersion())
                .listDatabases(request));

        if (!result.success()) {
            throw new ServiceException(DataSourceErrorCode.CONNECTION_FAILED, result.message());
        }
        return result.databases().stream().map(this::toDatabaseView).toList();
    }

    /**
     * 分页查询数据源表清单。
     * 前置校验：数据源必须 ACTIVE，Connector 必须声明 METADATA_INSPECT 能力。
     */
    @ResourceAuthorize(businessId = "#datasourceId",
        sourceModule = DataSourceAuthorizationCodes.MODULE,
        resourceType = DataSourceAuthorizationCodes.RESOURCE_TYPE,
        action = DataSourceAuthorizationCodes.Action.METADATA_INSPECT,
        purpose = DataSourceAuthorizationCodes.Purpose.METADATA_INSPECTION)
    public List<TableView> listTables(Long datasourceId, String catalog, String schema,
                                      String tableNamePattern, String tableType, int offset, int limit) {
        DataSourceInstance instance = requireActive(datasourceId);
        requireCapability(instance, "METADATA_INSPECT");
        int safeLimit = Math.min(Math.max(limit, 1), MAX_TABLE_LIMIT);
        int safeOffset = Math.max(offset, 0);

        MetadataInspectRequest request = buildRequest(instance, catalog, schema, tableNamePattern, tableType, safeOffset, safeLimit);
        MetadataInspectResult result = executeOnPool(request.getTenantId(), () ->
            inspectorRegistry.require(instance.getConnectorCode(), instance.getConnectorVersion())
                .listTables(request));

        if (!result.success()) {
            throw new ServiceException(DataSourceErrorCode.CONNECTION_FAILED, result.message());
        }
        return result.tables().stream().map(this::toTableView).toList();
    }

    /**
     * 批量查询指定表的字段结构。
     * @param datasourceId 数据源ID
     * @param tableNames 表名列表，最多 500 张
     * @return 表结构视图列表
     */
    @ResourceAuthorize(businessId = "#datasourceId",
        sourceModule = DataSourceAuthorizationCodes.MODULE,
        resourceType = DataSourceAuthorizationCodes.RESOURCE_TYPE,
        action = DataSourceAuthorizationCodes.Action.METADATA_INSPECT,
        purpose = DataSourceAuthorizationCodes.Purpose.METADATA_INSPECTION)
    public List<TableSchemaView> inspectColumns(Long datasourceId, String catalog, String schema,
                                                List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "表名列表不能为空");
        }
        if (tableNames.size() > MAX_BATCH_SIZE) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR,
                "单批探测表数量不能超过 " + MAX_BATCH_SIZE + " 张");
        }
        DataSourceInstance instance = requireActive(datasourceId);
        requireCapability(instance, "METADATA_INSPECT");

        MetadataInspectRequest request = buildRequest(instance, catalog, schema, null, null, 0, 0);
        MetadataInspectResult result = executeOnPool(request.getTenantId(), () ->
            inspectorRegistry.require(instance.getConnectorCode(), instance.getConnectorVersion())
                .inspectColumns(request, tableNames));

        if (!result.success()) {
            throw new ServiceException(DataSourceErrorCode.CONNECTION_FAILED, result.message());
        }
        return result.tableSchemas().stream().map(this::toTableSchemaView).toList();
    }

    /**
     * 为元数据采集模块生成单数据源的受控采集快照。
     *
     * <p>该方法复用现有 Connector、SecretRef、超时和资源授权链路；调用方无法指定原始 SQL 或物理连接参数。</p>
     */
    @Override
    @ResourceAuthorize(businessId = "#request.datasourceId",
        sourceModule = DataSourceAuthorizationCodes.MODULE,
        resourceType = DataSourceAuthorizationCodes.RESOURCE_TYPE,
        action = DataSourceAuthorizationCodes.Action.METADATA_INSPECT,
        purpose = DataSourceAuthorizationCodes.Purpose.METADATA_INSPECTION)
    public MetadataDiscoveryResult discover(MetadataDiscoveryRequest request, MetadataDiscoverySink sink) {
        if (request == null || request.getDatasourceId() == null) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "元数据采集请求缺少数据源");
        }
        if (sink == null) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "元数据采集请求缺少批次接收器");
        }
        int limit = request.getMaxTableCount() == null ? MAX_TABLE_LIMIT
            : Math.min(Math.max(request.getMaxTableCount(), 1), MAX_TABLE_LIMIT);
        DataSourceInstance instance = requireActive(request.getDatasourceId());
        requireCapability(instance, "METADATA_INSPECT");
        Set<String> emittedNamespaceKeys = new HashSet<>();
        int offset = 0;
        int namespaceCount = 0;
        int tableCount = 0;
        while (tableCount < limit) {
            int pageSize = Math.min(MAX_BATCH_SIZE, limit - tableCount);
            List<TableView> tables = listTables(instance.getDatasourceId(), request.getCatalogName(),
                request.getSchemaName(), null, null, offset, pageSize);
            if (tables.isEmpty()) break;
            List<TableSchemaView> schemas = inspectDiscoveredTables(instance.getDatasourceId(), tables);
            List<CollectedMetadataNamespace> namespaces = flatNamespaces(schemas).stream()
                .filter(value -> emittedNamespaceKeys.add(value.getCanonicalKey())).toList();
            if (!namespaces.isEmpty()) {
                sink.acceptNamespaces(namespaces);
                namespaceCount += namespaces.size();
            }
            sink.acceptTables(schemas.stream().map(this::toCollectedTable).toList());
            tableCount += schemas.size();
            offset += tables.size();
            if (tables.size() < pageSize) break;
        }
        boolean complete = listTables(instance.getDatasourceId(), request.getCatalogName(), request.getSchemaName(),
            null, null, offset, 1).isEmpty();
        return MetadataDiscoveryResult.builder()
            .tenantId(TenantHelper.getTenantId())
            .harvestRunId(request.getHarvestRunId())
            .datasourceId(instance.getDatasourceId())
            .datasourceResourceId(instance.getAuthzResourceId())
            .ownerDeptId(instance.getOwnerDeptId())
            .connectorCode(instance.getConnectorCode())
            .connectorVersion(instance.getConnectorVersion())
            .namespaceCount(namespaceCount)
            .tableCount(tableCount)
            .complete(complete)
            .build();
    }

    // ── 线程池隔离执行 ──
    private <T> T executeOnPool(String tenantId, Supplier<T> task) {
        try {
            return CompletableFuture.supplyAsync(() -> TenantHelper.dynamic(tenantId, task), connectionTestExecutor)
                .orTimeout(HARD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof TimeoutException) {
                throw new ServiceException(DataSourceErrorCode.CONNECTION_FAILED, "元数据探测超时，请检查网络和数据源状态");
            }
            if (e.getCause() instanceof ServiceException se) {
                throw se;
            }
            throw new ServiceException(DataSourceErrorCode.CONNECTION_FAILED, "元数据探测失败");
        } catch (java.util.concurrent.RejectedExecutionException e) {
            throw new ServiceException(GlobalErrorCode.BUSINESS_ERROR, "探测服务繁忙，请稍后重试");
        }
    }

    private DataSourceInstance requireActive(Long datasourceId) {
        DataSourceInstance instance = datasourceId == null ? null : instanceMapper.selectById(datasourceId);
        if (instance == null) {
            throw new ServiceException(DataSourceErrorCode.NOT_FOUND);
        }
        if (!"ACTIVE".equals(instance.getStatus())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "数据源必须处于启用状态才能探测元数据");
        }
        return instance;
    }

    private void requireCapability(DataSourceInstance instance, String capability) {
        ConnectorDescriptor descriptor = connectorCatalog.require(instance.getConnectorCode());
        if (!descriptor.getCapabilities().contains(capability)) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR,
                "该 Connector 不支持" + capability + "能力");
        }
    }

    private MetadataInspectRequest buildRequest(DataSourceInstance instance, String catalog, String schema,
                                                 String tableNamePattern, String tableType, int offset, int limit) {
        Map<String, Object> endpoint = parseEndpoint(instance);
        SubjectContext subject = subjectProvider.currentSubject();
        return MetadataInspectRequest.builder()
            .datasourceId(instance.getDatasourceId())
            .connectorCode(instance.getConnectorCode())
            .connectorVersion(instance.getConnectorVersion())
            .authProfileCode(instance.getAuthProfileCode())
            .endpointConfig(endpoint)
            .secretRef(instance.getSecretRef())
            .tenantId(instance.getTenantId())
            .actorType(subject.getPrincipalType() == null ? "HUMAN" : subject.getPrincipalType().name())
            .actorId(subject.getPrincipalId() == null ? String.valueOf(subject.getUserId()) : subject.getPrincipalId())
            .purpose(DataSourceSecretUsage.METADATA_INSPECT.code())
            .traceId(MDC.get("traceId"))
            .catalog(catalog)
            .schema(schema)
            .tableNamePattern(tableNamePattern)
            .tableType(tableType)
            .offset(offset)
            .limit(limit)
            .build();
    }

    private Map<String, Object> parseEndpoint(DataSourceInstance instance) {
        if (instance.getEndpointConfigJson() == null || instance.getEndpointConfigJson().isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> stored = JsonUtils.parseObject(instance.getEndpointConfigJson(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            return stored == null ? Map.of() : stored;
        } catch (Throwable e) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                    instance.getEndpointConfigJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            } catch (Exception ex) {
                return Map.of();
            }
        }
    }

    private DatabaseView toDatabaseView(DatabaseMeta meta) {
        return DatabaseView.builder().catalog(meta.getCatalog()).schema(meta.getSchema())
            .databaseName(meta.getDatabaseName()).remarks(meta.getRemarks()).build();
    }

    private TableView toTableView(TableMeta meta) {
        return TableView.builder().catalog(meta.getCatalog()).schema(meta.getSchema())
            .tableName(meta.getTableName()).tableType(meta.getTableType()).remarks(meta.getRemarks()).build();
    }

    private TableSchemaView toTableSchemaView(TableSchema schema) {
        return TableSchemaView.builder()
            .table(toTableView(schema.getTable()))
            .columns(schema.getColumns().stream().map(this::toColumnView).toList())
            .build();
    }

    private ColumnView toColumnView(ColumnMeta meta) {
        return ColumnView.builder().columnName(meta.getColumnName()).dataType(meta.getDataType())
            .jdbcType(meta.getJdbcType()).columnSize(meta.getColumnSize()).decimalDigits(meta.getDecimalDigits())
            .nullable(meta.isNullable()).primaryKey(meta.isPrimaryKey()).defaultValue(meta.getDefaultValue())
            .remarks(meta.getRemarks()).ordinalPosition(meta.getOrdinalPosition()).build();
    }

    private CollectedMetadataTable toCollectedTable(TableSchemaView schema) {
        TableView table = schema.getTable();
        String parentKey = namespaceKey(table.getCatalog(), table.getSchema());
        String tableKey = childKey(parentKey, MetadataObjectType.TABLE, table.getTableName());
        return CollectedMetadataTable.builder()
            .tableName(table.getTableName())
            .canonicalKey(tableKey)
            .parentCanonicalKey(parentKey)
            .tableType(table.getTableType())
            .comment(table.getRemarks())
            .columns(schema.getColumns().stream().map(value -> toCollectedColumn(tableKey, value)).toList())
            .build();
    }

    private List<CollectedMetadataNamespace> flatNamespaces(List<TableSchemaView> schemas) {
        Map<String, CollectedMetadataNamespace> values = new LinkedHashMap<>();
        for (TableSchemaView schema : schemas) {
            String catalog = StringUtils.trimToNull(schema.getTable().getCatalog());
            String schemaName = StringUtils.trimToNull(schema.getTable().getSchema());
            String catalogKey = catalog == null ? null : childKey(null, MetadataObjectType.CATALOG, catalog);
            if (catalogKey != null) {
                values.putIfAbsent(catalogKey, CollectedMetadataNamespace.builder()
                    .namespaceType(MetadataObjectType.CATALOG).namespaceName(catalog)
                    .canonicalKey(catalogKey).build());
            }
            if (schemaName != null) {
                String schemaKey = childKey(catalogKey, MetadataObjectType.SCHEMA, schemaName);
                values.putIfAbsent(schemaKey, CollectedMetadataNamespace.builder()
                    .namespaceType(MetadataObjectType.SCHEMA).namespaceName(schemaName)
                    .canonicalKey(schemaKey).parentCanonicalKey(catalogKey).build());
            }
        }
        return List.copyOf(values.values());
    }

    private String namespaceKey(String catalog, String schema) {
        String catalogName = StringUtils.trimToNull(catalog);
        String schemaName = StringUtils.trimToNull(schema);
        String catalogKey = catalogName == null ? null : childKey(null, MetadataObjectType.CATALOG, catalogName);
        return schemaName == null ? catalogKey : childKey(catalogKey, MetadataObjectType.SCHEMA, schemaName);
    }

    private String childKey(String parentKey, MetadataObjectType type, String name) {
        String value = name == null ? "" : name.trim();
        String own = type.getCode() + '/' + value.length() + ':' + value;
        return parentKey == null ? own : parentKey + '/' + own;
    }

    /**
     * 按 Catalog/Schema 定位分组拉取字段，避免不同命名空间下同名表发生串表。
     */
    private List<TableSchemaView> inspectDiscoveredTables(Long datasourceId, List<TableView> tables) {
        if (tables.isEmpty()) return List.of();
        Map<TableNamespace, List<TableView>> grouped = new LinkedHashMap<>();
        for (TableView table : tables) {
            TableNamespace namespace = new TableNamespace(StringUtils.trimToNull(table.getCatalog()),
                StringUtils.trimToNull(table.getSchema()));
            grouped.computeIfAbsent(namespace, ignored -> new ArrayList<>()).add(table);
        }
        List<TableSchemaView> schemas = new ArrayList<>();
        grouped.forEach((namespace, locatedTables) -> {
            for (int from = 0; from < locatedTables.size(); from += MAX_BATCH_SIZE) {
                int to = Math.min(from + MAX_BATCH_SIZE, locatedTables.size());
                List<TableView> batch = locatedTables.subList(from, to);
                Map<String, TableView> locatorByName = batch.stream()
                    .collect(Collectors.toMap(TableView::getTableName, value -> value));
                List<TableSchemaView> inspected = inspectColumns(datasourceId, namespace.catalog(), namespace.schema(),
                    batch.stream().map(TableView::getTableName).toList());
                Set<String> returnedNames = new HashSet<>();
                for (TableSchemaView schema : inspected) {
                    TableView locator = locatorByName.get(schema.getTable().getTableName());
                    if (locator == null || !returnedNames.add(schema.getTable().getTableName())) {
                        throw new ServiceException(DataSourceErrorCode.CONNECTION_FAILED,
                            "字段探查返回了未请求或重复的表");
                    }
                    schemas.add(TableSchemaView.builder().table(locator).columns(schema.getColumns()).build());
                }
                if (returnedNames.size() != batch.size()) {
                    throw new ServiceException(DataSourceErrorCode.CONNECTION_FAILED, "字段探查结果不完整");
                }
            }
        });
        return List.copyOf(schemas);
    }

    private CollectedMetadataColumn toCollectedColumn(String tableKey, ColumnView column) {
        return CollectedMetadataColumn.builder()
            .columnName(column.getColumnName())
            .canonicalKey(childKey(tableKey, MetadataObjectType.COLUMN, column.getColumnName()))
            .dataType(column.getDataType())
            .jdbcType(column.getJdbcType())
            .lengthValue(column.getColumnSize())
            .precisionValue(column.getColumnSize())
            .scaleValue(column.getDecimalDigits())
            .nullableFlag(column.isNullable())
            .primaryKeyFlag(column.isPrimaryKey())
            .defaultExpression(column.getDefaultValue())
            .comment(column.getRemarks())
            .ordinalPosition(column.getOrdinalPosition())
            .build();
    }

    private record TableNamespace(String catalog, String schema) { }
}
