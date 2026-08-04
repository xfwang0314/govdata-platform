package com.xfwang.platform.auth.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xfwang.platform.auth.application.GrantAdministrationService;
import com.xfwang.platform.auth.application.model.GrantDraftCommand;
import com.xfwang.platform.auth.application.model.GrantRevokeCommand;
import com.xfwang.platform.auth.application.model.GrantView;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** AuthorizationGrant 生命周期管理入口。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/platform/auth/grants")
public class AuthzGrantController {

    private final GrantAdministrationService grantService;

    @SaCheckPermission("platform:auth:grant:add")
    @Log(title = "运行时授权", businessType = BusinessType.GRANT)
    @PostMapping
    public R<GrantView> create(@Validated @RequestBody GrantDraftCommand command) {
        return R.ok(grantService.createDraft(command));
    }

    @SaCheckPermission("platform:auth:grant:activate")
    @Log(title = "激活运行时授权", businessType = BusinessType.GRANT)
    @PutMapping("/{grantId}/activate")
    public R<GrantView> activate(@PathVariable Long grantId) {
        return R.ok(grantService.activate(grantId));
    }

    @SaCheckPermission("platform:auth:grant:revoke")
    @Log(title = "撤销运行时授权", businessType = BusinessType.UPDATE)
    @PutMapping("/{grantId}/revoke")
    public R<GrantView> revoke(@PathVariable Long grantId,
                               @Validated @RequestBody GrantRevokeCommand command) {
        return R.ok(grantService.revoke(grantId, command.getReason()));
    }

    @SaCheckPermission("platform:auth:grant:query")
    @GetMapping("/{grantId}")
    public R<GrantView> get(@PathVariable Long grantId) {
        return R.ok(grantService.get(grantId));
    }
}
