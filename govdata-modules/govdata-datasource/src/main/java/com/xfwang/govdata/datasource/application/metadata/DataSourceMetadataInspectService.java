package com.xfwang.govdata.datasource.application.metadata;

import com.xfwang.govdata.datasource.application.metadata.view.ColumnView;
import com.xfwang.govdata.datasource.application.metadata.view.DatabaseView;
import com.xfwang.govdata.datasource.application.metadata.view.TableSchemaView;
import com.xfwang.govdata.datasource.application.metadata.view.TableView;
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
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.tenant.helper.TenantHelper;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * 数据源元数据探测应用服务。
 *
 * <p>提供数据库清单、表清单分页查询和批量字段结构探测功能，探测任务提交到隔离线程池执行。</p>
 */
@Service
@RequiredArgsConstructor
public class DataSourceMetadataInspectService {

    private static final int MAX_BATCH_SIZE = 500;
    private static final int MAX_TABLE_LIMIT = 1000;
    private static final long HARD_TIMEOUT_SECONDS = 30;

    private final DataSourceInstanceMapper instanceMapper;
    private final MetadataInspectorRegistry inspectorRegistry;
    private final ConnectorCatalog connectorCatalog;

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
        MetadataInspectResult result = executeOnPool(() ->
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
        MetadataInspectResult result = executeOnPool(() ->
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
        MetadataInspectResult result = executeOnPool(() ->
            inspectorRegistry.require(instance.getConnectorCode(), instance.getConnectorVersion())
                .inspectColumns(request, tableNames));

        if (!result.success()) {
            throw new ServiceException(DataSourceErrorCode.CONNECTION_FAILED, result.message());
        }
        return result.tableSchemas().stream().map(this::toTableSchemaView).toList();
    }

    // ── 线程池隔离执行 ──
    private <T> T executeOnPool(Supplier<T> task) {
        try {
            return CompletableFuture.supplyAsync(task, connectionTestExecutor)
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
        return MetadataInspectRequest.builder()
            .datasourceId(instance.getDatasourceId())
            .connectorCode(instance.getConnectorCode())
            .connectorVersion(instance.getConnectorVersion())
            .authProfileCode(instance.getAuthProfileCode())
            .endpointConfig(endpoint)
            .secretRef(instance.getSecretRef())
            .tenantId(TenantHelper.getTenantId())
            .actorType("USER")
            .actorId(LoginHelper.getUserIdStr())
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
}
