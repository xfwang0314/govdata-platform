package com.xfwang.govdata.metadata.harvest.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xfwang.govdata.metadata.harvest.application.plan.HarvestPlanService;
import com.xfwang.govdata.metadata.harvest.application.plan.command.HarvestPlanCommand;
import com.xfwang.govdata.metadata.harvest.application.plan.query.HarvestPlanQuery;
import com.xfwang.govdata.metadata.harvest.application.plan.view.HarvestPlanView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 元数据采集计划与规则配置 REST 接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/govdata/metadata-harvest")
public class HarvestPlanController {
    private final HarvestPlanService planService;

    @SaCheckPermission("govdata:metadata:harvest:list")
    @GetMapping("/plans")
    public R<PageResult<HarvestPlanView>> plans(HarvestPlanQuery query, PageQuery pageQuery) {
        return R.ok(planService.page(query, pageQuery));
    }

    @SaCheckPermission("govdata:metadata:harvest:query")
    @GetMapping("/plans/{planId}")
    public R<HarvestPlanView> plan(@NotNull @PathVariable Long planId) {
        return R.ok(planService.get(planId));
    }

    @SaCheckPermission("govdata:metadata:harvest:add")
    @PostMapping("/plans")
    public R<HarvestPlanView> create(@Valid @RequestBody HarvestPlanCommand command) {
        return R.ok(planService.create(command));
    }

    @SaCheckPermission("govdata:metadata:harvest:edit")
    @PutMapping("/plans/{planId}")
    public R<HarvestPlanView> update(@NotNull @PathVariable Long planId,
                                     @Valid @RequestBody HarvestPlanCommand command) {
        return R.ok(planService.update(planId, command));
    }

    @SaCheckPermission("govdata:metadata:harvest:remove")
    @DeleteMapping("/plans/{planId}")
    public R<Void> remove(@NotNull @PathVariable Long planId) {
        planService.remove(planId);
        return R.ok();
    }

}
