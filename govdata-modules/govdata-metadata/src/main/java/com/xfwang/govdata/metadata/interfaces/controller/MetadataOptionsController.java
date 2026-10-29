package com.xfwang.govdata.metadata.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xfwang.govdata.metadata.application.catalog.MetadataOptionsService;
import com.xfwang.govdata.metadata.application.catalog.view.MetadataOptionsView;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 元数据固定类型和状态选项接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/govdata/metadata/options")
public class MetadataOptionsController {

    private final MetadataOptionsService optionsService;

    @SaCheckPermission("govdata:metadata:list")
    @GetMapping
    public R<MetadataOptionsView> options() {
        return R.ok(optionsService.options());
    }
}
