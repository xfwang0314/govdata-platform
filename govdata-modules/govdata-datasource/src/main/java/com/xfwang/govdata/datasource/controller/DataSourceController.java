package com.xfwang.govdata.datasource.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xfwang.govdata.datasource.application.DataSourceApplicationService;
import com.xfwang.govdata.datasource.connector.ConnectorCatalog;
import com.xfwang.govdata.datasource.connector.ConnectorDescriptor;
import com.xfwang.govdata.datasource.model.ConnectionTestView;
import com.xfwang.govdata.datasource.model.DataSourceCommand;
import com.xfwang.govdata.datasource.model.DataSourceQuery;
import com.xfwang.govdata.datasource.model.DataSourceView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 数据源管理与授权联调入口。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/govdata/datasources")
public class DataSourceController {

    private final DataSourceApplicationService service;
    private final ConnectorCatalog connectorCatalog;

    @SaCheckPermission("govdata:datasource:list")
    @GetMapping("/connectors")
    public R<List<ConnectorDescriptor>> connectors() {
        return R.ok(connectorCatalog.list());
    }

    @SaCheckPermission("govdata:datasource:list")
    @GetMapping
    public R<PageResult<DataSourceView>> page(DataSourceQuery query, PageQuery pageQuery) {
        return R.ok(service.page(query, pageQuery));
    }

    @SaCheckPermission("govdata:datasource:query")
    @GetMapping("/{datasourceId}")
    public R<DataSourceView> get(@NotNull @PathVariable Long datasourceId) {
        return R.ok(service.get(datasourceId));
    }

    @SaCheckPermission("govdata:datasource:add")
    @Log(title = "数据源", businessType = BusinessType.INSERT)
    @PostMapping
    public R<DataSourceView> create(@Valid @RequestBody DataSourceCommand command) {
        return R.ok(service.create(command));
    }

    @SaCheckPermission("govdata:datasource:edit")
    @Log(title = "数据源", businessType = BusinessType.UPDATE)
    @PutMapping("/{datasourceId}")
    public R<DataSourceView> update(@NotNull @PathVariable Long datasourceId,
                                    @Valid @RequestBody DataSourceCommand command) {
        return R.ok(service.update(datasourceId, command));
    }

    @SaCheckPermission("govdata:datasource:test")
    @Log(title = "数据源连接测试", businessType = BusinessType.OTHER)
    @PostMapping("/{datasourceId}/connection-tests")
    public R<ConnectionTestView> test(@NotNull @PathVariable Long datasourceId,
                                      @NotBlank @RequestParam String purpose) {
        return R.ok(service.test(datasourceId, purpose));
    }

    @SaCheckPermission("govdata:datasource:test:list")
    @GetMapping("/{datasourceId}/connection-tests")
    public R<PageResult<ConnectionTestView>> testHistory(@NotNull @PathVariable Long datasourceId,
                                                         PageQuery pageQuery) {
        return R.ok(service.testHistory(datasourceId, pageQuery));
    }

    @SaCheckPermission("govdata:datasource:edit")
    @Log(title = "启用数据源", businessType = BusinessType.UPDATE)
    @PutMapping("/{datasourceId}/enable")
    public R<DataSourceView> enable(@NotNull @PathVariable Long datasourceId) {
        return R.ok(service.changeStatus(datasourceId, "ACTIVE"));
    }

    @SaCheckPermission("govdata:datasource:edit")
    @Log(title = "停用数据源", businessType = BusinessType.UPDATE)
    @PutMapping("/{datasourceId}/disable")
    public R<DataSourceView> disable(@NotNull @PathVariable Long datasourceId) {
        return R.ok(service.changeStatus(datasourceId, "DISABLED"));
    }

    @SaCheckPermission("govdata:datasource:remove")
    @Log(title = "删除数据源", businessType = BusinessType.DELETE)
    @DeleteMapping("/{datasourceId}")
    public R<Void> remove(@NotNull @PathVariable Long datasourceId) {
        service.remove(datasourceId);
        return R.ok();
    }
}
