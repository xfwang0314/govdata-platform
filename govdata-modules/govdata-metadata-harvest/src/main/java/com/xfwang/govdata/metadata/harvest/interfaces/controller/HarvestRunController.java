package com.xfwang.govdata.metadata.harvest.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xfwang.govdata.metadata.harvest.application.run.HarvestRunService;
import com.xfwang.govdata.metadata.harvest.application.run.query.HarvestRunQuery;
import com.xfwang.govdata.metadata.harvest.application.run.view.HarvestRunView;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 元数据采集运行记录与手动执行 REST 接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/govdata/metadata-harvest")
public class HarvestRunController {
    private final HarvestRunService runService;

    @SaCheckPermission("govdata:metadata:harvest:execute")
    @PostMapping("/plans/{planId}/execute")
    public R<HarvestRunView> execute(@NotNull @PathVariable Long planId) {
        return R.ok(runService.executeManual(planId));
    }

    @SaCheckPermission("govdata:metadata:harvest:execute")
    @PostMapping("/runs/{runId}/retry")
    public R<HarvestRunView> retry(@NotNull @PathVariable Long runId) {
        return R.ok(runService.retry(runId));
    }

    @SaCheckPermission("govdata:metadata:harvest:run:list")
    @GetMapping("/plans/{planId}/runs")
    public R<PageResult<HarvestRunView>> runs(@NotNull @PathVariable Long planId, HarvestRunQuery query, PageQuery pageQuery) {
        return R.ok(runService.page(planId, query, pageQuery));
    }

    @SaCheckPermission("govdata:metadata:harvest:run:query")
    @GetMapping("/runs/{runId}")
    public R<HarvestRunView> run(@NotNull @PathVariable Long runId) {
        return R.ok(runService.get(runId));
    }
}
