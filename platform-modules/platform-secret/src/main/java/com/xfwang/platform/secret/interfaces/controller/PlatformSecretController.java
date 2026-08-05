package com.xfwang.platform.secret.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xfwang.platform.secret.application.management.SecretManagementService;
import com.xfwang.platform.secret.application.management.command.SecretCreateCommand;
import com.xfwang.platform.secret.application.management.query.SecretQuery;
import com.xfwang.platform.secret.application.management.command.SecretRotateCommand;
import com.xfwang.platform.secret.application.management.command.SecretUpdateCommand;
import com.xfwang.platform.secret.application.management.view.SecretView;
import com.xfwang.platform.secret.application.options.view.SecretOptionsView;
import com.xfwang.platform.secret.application.options.SecretOptionsQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/platform/secrets")
public class PlatformSecretController {
    private final SecretManagementService service;
    private final SecretOptionsQueryService optionsQueryService;

    @SaCheckPermission("platform:secret:list")
    @GetMapping
    public R<PageResult<SecretView>> page(SecretQuery query, PageQuery pageQuery) {
        return R.ok(service.page(query, pageQuery));
    }

    @SaCheckPermission("platform:secret:list")
    @GetMapping("/options")
    public R<SecretOptionsView> options() { return R.ok(optionsQueryService.options()); }

    @SaCheckPermission("platform:secret:query")
    @GetMapping("/{secretId}")
    public R<SecretView> get(@NotNull @PathVariable Long secretId) { return R.ok(service.get(secretId)); }

    @SaCheckPermission("platform:secret:add")
    @Log(title = "平台凭据", businessType = BusinessType.INSERT, isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping
    public R<SecretView> create(@Valid @RequestBody SecretCreateCommand command) { return R.ok(service.create(command)); }

    @SaCheckPermission("platform:secret:rotate")
    @Log(title = "轮换平台凭据", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/{secretId}/rotate")
    public R<SecretView> rotate(@NotNull @PathVariable Long secretId,
                                @Valid @RequestBody SecretRotateCommand command) {
        return R.ok(service.rotate(secretId, command));
    }

    @SaCheckPermission("platform:secret:edit")
    @Log(title = "修改凭据元数据", businessType = BusinessType.UPDATE, isSaveRequestData = false, isSaveResponseData = false)
    @PutMapping("/{secretId}")
    public R<SecretView> update(@NotNull @PathVariable Long secretId,
                                @Valid @RequestBody SecretUpdateCommand command) {
        return R.ok(service.update(secretId, command));
    }

    @SaCheckPermission("platform:secret:edit")
    @PutMapping("/{secretId}/enable")
    public R<SecretView> enable(@NotNull @PathVariable Long secretId) {
        return R.ok(service.changeStatus(secretId, "ACTIVE"));
    }

    @SaCheckPermission("platform:secret:edit")
    @PutMapping("/{secretId}/disable")
    public R<SecretView> disable(@NotNull @PathVariable Long secretId) {
        return R.ok(service.changeStatus(secretId, "DISABLED"));
    }
}
