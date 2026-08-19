package com.xfwang.govdata.datasource.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xfwang.govdata.datasource.application.catalog.ConnectorCatalogQueryService;
import com.xfwang.govdata.datasource.application.authorization.view.ProjectionReconcileView;
import com.xfwang.govdata.datasource.application.connectiontest.DataSourceConnectionTestService;
import com.xfwang.govdata.datasource.application.connectiontest.view.ConnectionTestView;
import com.xfwang.govdata.datasource.application.management.DataSourceManagementService;
import com.xfwang.govdata.datasource.application.management.command.DataSourceCommand;
import com.xfwang.govdata.datasource.application.management.query.DataSourceQuery;
import com.xfwang.govdata.datasource.application.management.view.DataSourceView;
import com.xfwang.govdata.datasource.application.options.DataSourceOptionsQueryService;
import com.xfwang.govdata.datasource.application.options.view.DataSourceOptionsView;
import com.xfwang.govdata.datasource.application.metadata.DataSourceMetadataInspectService;
import com.xfwang.govdata.datasource.application.metadata.view.DatabaseView;
import com.xfwang.govdata.datasource.application.metadata.view.TableView;
import com.xfwang.govdata.datasource.application.metadata.view.TableSchemaView;
import com.xfwang.govdata.datasource.domain.connector.ConnectorDescriptor;
import com.xfwang.platform.api.secret.SecretReference;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 政务数据源管理 REST 接口控制器。
 *
 * <p>提供数据源 Connector 目录、下拉字典选项、可用凭据引用查询、数据源 CRUD、启用/停用以及连通测试等 HTTP API 接口。</p>
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/govdata/datasources")
public class DataSourceController {

    private final DataSourceManagementService service;
    private final DataSourceConnectionTestService connectionTestService;
    private final ConnectorCatalogQueryService connectorCatalogQueryService;
    private final DataSourceOptionsQueryService optionsQueryService;
    private final DataSourceMetadataInspectService metadataInspectService;

    /**
     * 查询系统注册的所有 Connector 描述符列表。
     *
     * @return Connector 描述符列表
     */
    @SaCheckPermission("govdata:datasource:list")
    @GetMapping("/connectors")
    public R<List<ConnectorDescriptor>> connectors() {
        return R.ok(connectorCatalogQueryService.list());
    }

    /**
     * 查询数据源下拉选项与字典元数据。
     *
     * @return 选项汇总视图
     */
    @SaCheckPermission("govdata:datasource:list")
    @GetMapping("/options")
    public R<DataSourceOptionsView> options() {
        return R.ok(optionsQueryService.options());
    }

    /**
     * 查询当前调用模块可用于指定 Connector 测试的凭据引用列表。
     *
     * @param connectorCode Connector 编码
     * @return 可用凭据引用列表
     */
    @SaCheckPermission("govdata:datasource:list")
    @GetMapping("/secret-references")
    public R<List<SecretReference>> secretReferences(
        @NotBlank @RequestParam String connectorCode) {
        return R.ok(connectorCatalogQueryService.listSecretReferences(connectorCode));
    }

    /**
     * 分页查询数据源列表。
     *
     * @param query     查询过滤条件
     * @param pageQuery 分页参数
     * @return 数据源视图分页结果
     */
    @SaCheckPermission("govdata:datasource:list")
    @GetMapping
    public R<PageResult<DataSourceView>> page(DataSourceQuery query, PageQuery pageQuery) {
        return R.ok(service.page(query, pageQuery));
    }

    /**
     * 获取指定数据源详情。
     *
     * @param datasourceId 数据源 ID
     * @return 数据源视图
     */
    @SaCheckPermission("govdata:datasource:query")
    @GetMapping("/{datasourceId}")
    public R<DataSourceView> get(@NotNull @PathVariable Long datasourceId) {
        return R.ok(service.get(datasourceId));
    }

    /**
     * 新增数据源。
     *
     * @param command 创建命令参数
     * @return 创建后的数据源视图
     */
    @SaCheckPermission("govdata:datasource:add")
    @Log(title = "数据源", businessType = BusinessType.INSERT)
    @PostMapping
    public R<DataSourceView> create(@Valid @RequestBody DataSourceCommand command) {
        return R.ok(service.create(command));
    }

    /**
     * 修改现有数据源。
     *
     * @param datasourceId 数据源 ID
     * @param command      更新命令参数
     * @return 更新后的数据源视图
     */
    @SaCheckPermission("govdata:datasource:edit")
    @Log(title = "数据源", businessType = BusinessType.UPDATE)
    @PutMapping("/{datasourceId}")
    public R<DataSourceView> update(@NotNull @PathVariable Long datasourceId,
                                    @Valid @RequestBody DataSourceCommand command) {
        return R.ok(service.update(datasourceId, command));
    }

    /**
     * 触发数据源连通测试。
     *
     * @param datasourceId 数据源 ID
     * @return 连通测试结果视图
     */
    @SaCheckPermission("govdata:datasource:test")
    @Log(title = "数据源连接测试", businessType = BusinessType.OTHER)
    @PostMapping("/{datasourceId}/connection-tests")
    public R<ConnectionTestView> test(@NotNull @PathVariable Long datasourceId) {
        return R.ok(connectionTestService.test(datasourceId));
    }

    /**
     * 分页查询数据源连通测试历史记录。
     *
     * @param datasourceId 数据源 ID
     * @param pageQuery    分页参数
     * @return 连通测试历史分页结果
     */
    @SaCheckPermission("govdata:datasource:test:list")
    @GetMapping("/{datasourceId}/connection-tests")
    public R<PageResult<ConnectionTestView>> testHistory(@NotNull @PathVariable Long datasourceId,
                                                         PageQuery pageQuery) {
        return R.ok(connectionTestService.history(datasourceId, pageQuery));
    }

    /**
     * 启用数据源。
     *
     * @param datasourceId 数据源 ID
     * @return 更新后的数据源视图
     */
    @SaCheckPermission("govdata:datasource:edit")
    @Log(title = "启用数据源", businessType = BusinessType.UPDATE)
    @PutMapping("/{datasourceId}/enable")
    public R<DataSourceView> enable(@NotNull @PathVariable Long datasourceId) {
        return R.ok(service.enable(datasourceId));
    }

    /**
     * 停用数据源。
     *
     * @param datasourceId 数据源 ID
     * @return 更新后的数据源视图
     */
    @SaCheckPermission("govdata:datasource:edit")
    @Log(title = "停用数据源", businessType = BusinessType.UPDATE)
    @PutMapping("/{datasourceId}/disable")
    public R<DataSourceView> disable(@NotNull @PathVariable Long datasourceId) {
        return R.ok(service.disable(datasourceId));
    }

    /**
     * 逻辑删除数据源（需先停用）。
     *
     * @param datasourceId 数据源 ID
     * @return 空响应
     */
    @SaCheckPermission("govdata:datasource:remove")
    @Log(title = "删除数据源", businessType = BusinessType.DELETE)
    @DeleteMapping("/{datasourceId}")
    public R<Void> remove(@NotNull @PathVariable Long datasourceId) {
        service.remove(datasourceId);
        return R.ok();
    }

    /** 分批重放历史数据源授权资源投影，修复缺失或属性漂移。 */
    @SaCheckPermission("govdata:datasource:edit")
    @Log(title = "数据源授权投影对账", businessType = BusinessType.OTHER)
    @PostMapping("/authorization-projections/reconcile")
    public R<ProjectionReconcileView> reconcileAuthorizationProjections(
        @RequestParam(defaultValue = "0") @Min(0) Long afterId,
        @RequestParam(defaultValue = "200") @Min(1) @Max(500) int limit) {
        return R.ok(service.reconcileAuthorizationProjections(afterId, limit));
    }

    /**
     * 查询数据源数据库 / Schema 清单。
     *
     * @param datasourceId 数据源 ID
     * @return 数据库元数据视图列表
     */
    @SaCheckPermission("govdata:datasource:metadata:databases")
    @GetMapping("/{datasourceId}/metadata/databases")
    public R<List<DatabaseView>> listDatabases(@NotNull @PathVariable Long datasourceId) {
        return R.ok(metadataInspectService.listDatabases(datasourceId));
    }

    /**
     * 分页查询数据源表清单。
     *
     * @param datasourceId 数据源 ID
     * @param catalog Catalog 名称
     * @param schema Schema 名称
     * @param tableNamePattern 表名匹配模式
     * @param tableType 表类型
     * @param offset 偏移量
     * @param limit 每页数量
     * @return 表元数据视图列表
     */
    @SaCheckPermission("govdata:datasource:metadata:tables")
    @GetMapping("/{datasourceId}/metadata/tables")
    public R<List<TableView>> listTables(
            @NotNull @PathVariable Long datasourceId,
            @RequestParam(required = false) String catalog,
            @RequestParam(required = false) String schema,
            @RequestParam(required = false) String tableNamePattern,
            @RequestParam(required = false) String tableType,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "100") int limit) {
        return R.ok(metadataInspectService.listTables(datasourceId, catalog, schema, tableNamePattern, tableType, offset, limit));
    }

    /**
     * 批量查询指定表的字段结构。
     *
     * @param datasourceId 数据源 ID
     * @param catalog Catalog 名称
     * @param schema Schema 名称
     * @param tableNames 表名列表
     * @return 表结构视图列表
     */
    @SaCheckPermission("govdata:datasource:metadata:columns")
    @PostMapping("/{datasourceId}/metadata/columns")
    public R<List<TableSchemaView>> inspectColumns(
            @NotNull @PathVariable Long datasourceId,
            @RequestParam(required = false) String catalog,
            @RequestParam(required = false) String schema,
            @Valid @RequestBody @Size(min = 1, max = 500) List<String> tableNames) {
        return R.ok(metadataInspectService.inspectColumns(datasourceId, catalog, schema, tableNames));
    }
}
