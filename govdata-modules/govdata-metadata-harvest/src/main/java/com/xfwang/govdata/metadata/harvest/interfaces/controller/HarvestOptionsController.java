package com.xfwang.govdata.metadata.harvest.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xfwang.govdata.metadata.harvest.application.plan.HarvestOptionsService;
import com.xfwang.govdata.metadata.harvest.application.plan.view.HarvestOptionsView;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 元数据采集计划、规则和运行状态选项接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/govdata/metadata-harvest/options")
public class HarvestOptionsController {

    private final HarvestOptionsService optionsService;

    @SaCheckPermission("govdata:metadata:harvest:list")
    @GetMapping
    public R<HarvestOptionsView> options() {
        return R.ok(optionsService.options());
    }
}
