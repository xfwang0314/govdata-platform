package com.xfwang.platform.auth.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xfwang.platform.auth.application.grant.GrantAdministrationService;
import com.xfwang.platform.auth.application.grant.command.GrantDraftCommand;
import com.xfwang.platform.auth.application.grant.command.GrantRevokeCommand;
import com.xfwang.platform.auth.application.grant.view.GrantView;
import com.xfwang.platform.auth.application.grant.query.GrantQuery;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
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

/**
 * AuthorizationGrant 生命周期管理入口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/platform/auth/grants")
public class AuthzGrantController {

    private final GrantAdministrationService grantService;

    @SaCheckPermission("platform:auth:grant:query")
    @GetMapping("/page")
    public R<PageResult<GrantView>> page(GrantQuery query, PageQuery pageQuery) {
        return R.ok(grantService.page(query, pageQuery));
    }

    @SaCheckPermission("platform:auth:grant:add")
    @Log(title = "运行时授权", businessType = BusinessType.GRANT)
    @PostMapping
    public R<GrantView> create(@Validated @RequestBody GrantDraftCommand command) {
        // HTTP 管理入口只能创建人工授权，来源和授权人必须来自可信服务端上下文。
        command.setSourceType("ADMIN");
        command.setSourceId("manual_" + Long.toUnsignedString(IdWorker.getId(), 36));
        command.setGrantorType("HUMAN");
        command.setGrantorId(LoginHelper.getUserIdStr());
        return R.ok(grantService.createDraft(command));
    }

    @SaCheckPermission("platform:auth:grant:edit")
    @Log(title = "修改运行时授权草稿", businessType = BusinessType.UPDATE)
    @PutMapping("/{grantId}/draft")
    public R<GrantView> updateDraft(@PathVariable Long grantId,
                                    @Validated @RequestBody GrantDraftCommand command) {
        command.setSourceType("ADMIN");
        command.setSourceId("manual_" + Long.toUnsignedString(IdWorker.getId(), 36));
        command.setGrantorType("HUMAN");
        command.setGrantorId(LoginHelper.getUserIdStr());
        return R.ok(grantService.updateDraft(grantId, command));
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

    @SaCheckPermission("platform:auth:grant:delete")
    @Log(title = "删除运行时授权", businessType = BusinessType.DELETE)
    @DeleteMapping("/{grantId}")
    public R<Void> delete(@PathVariable Long grantId,
                          @RequestParam(required = false) String reason) {
        grantService.delete(grantId, reason);
        return R.ok();
    }

    @SaCheckPermission("platform:auth:grant:delete")
    @Log(title = "恢复运行时授权", businessType = BusinessType.UPDATE)
    @PutMapping("/{grantId}/restore")
    public R<GrantView> restore(@PathVariable Long grantId) {
        return R.ok(grantService.restore(grantId));
    }
}
