package com.xfwang.platform.auth.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.xfwang.platform.auth.application.policy.PolicyGroupApplicationService;
import com.xfwang.platform.auth.application.policy.command.PolicyGroupDraftCommand;
import com.xfwang.platform.auth.application.policy.command.PolicyGroupDraftUpdateCommand;
import com.xfwang.platform.auth.application.policy.command.PolicyReasonCommand;
import com.xfwang.platform.auth.application.policy.query.PolicyGroupQuery;
import com.xfwang.platform.auth.application.policy.view.PolicyGroupView;
import com.xfwang.platform.auth.application.policy.view.PolicyOperationLogView;
import com.xfwang.platform.auth.application.policy.view.PolicyVersionView;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 策略组唯一管理与生命周期入口。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/platform/auth/policy-groups")
public class AuthzPolicyGroupController {

    private final PolicyGroupApplicationService groupService;

    @SaCheckPermission("platform:auth:policy:query")
    @GetMapping("/page")
    public R<PageResult<PolicyGroupView>> page(PolicyGroupQuery query, PageQuery pageQuery) {
        return R.ok(groupService.page(query, pageQuery));
    }

    @SaCheckPermission("platform:auth:policy:query")
    @GetMapping("/{groupCode}")
    public R<PolicyGroupView> get(@PathVariable String groupCode) {
        return R.ok(groupService.get(groupCode));
    }

    @SaCheckPermission("platform:auth:policy:add")
    @Log(title = "创建ABAC策略组待发布版本", businessType = BusinessType.INSERT)
    @PostMapping
    public R<PolicyGroupView> create(@Validated @RequestBody PolicyGroupDraftCommand command) {
        return R.ok(groupService.create(command));
    }

    @SaCheckPermission("platform:auth:policy:edit")
    @Log(title = "保存ABAC策略组待发布版本", businessType = BusinessType.UPDATE)
    @PutMapping("/{groupCode}/draft")
    public R<PolicyGroupView> updateDraft(@PathVariable String groupCode,
            @Validated @RequestBody PolicyGroupDraftUpdateCommand command) {
        return R.ok(groupService.updateDraft(groupCode, command));
    }

    @SaCheckPermission(value = {"platform:auth:policy:edit", "platform:auth:policy:version"},
        mode = SaMode.OR)
    @Log(title = "创建ABAC策略组新版本", businessType = BusinessType.INSERT)
    @PostMapping("/{groupCode}/versions")
    public R<PolicyGroupView> createNextVersion(@PathVariable String groupCode) {
        return R.ok(groupService.createNextVersion(groupCode));
    }

    @SaCheckPermission("platform:auth:policy:version")
    @Log(title = "从历史版本创建ABAC策略待发布版本", businessType = BusinessType.INSERT)
    @PostMapping("/{groupCode}/versions/{versionId}/draft")
    public R<PolicyGroupView> createDraftFromVersion(@PathVariable String groupCode,
                                                      @PathVariable Long versionId) {
        return R.ok(groupService.createDraftFromVersion(groupCode, versionId));
    }

    @SaCheckPermission("platform:auth:policy:publish")
    @Log(title = "发布ABAC策略组", businessType = BusinessType.UPDATE)
    @PutMapping("/{groupCode}/publish")
    public R<PolicyGroupView> publish(@PathVariable String groupCode) {
        return R.ok(groupService.publish(groupCode));
    }

    @SaCheckPermission("platform:auth:policy:disable")
    @Log(title = "停用ABAC策略组", businessType = BusinessType.UPDATE)
    @PutMapping("/{groupCode}/disable")
    public R<PolicyGroupView> disable(@PathVariable String groupCode,
                                      @Validated @RequestBody PolicyReasonCommand command) {
        return R.ok(groupService.disable(groupCode, command.getReason()));
    }

    @SaCheckPermission("platform:auth:policy:publish")
    @Log(title = "恢复启用ABAC策略组", businessType = BusinessType.UPDATE)
    @PutMapping("/{groupCode}/enable")
    public R<PolicyGroupView> enable(@PathVariable String groupCode) {
        return R.ok(groupService.enable(groupCode));
    }

    @SaCheckPermission("platform:auth:policy:edit")
    @Log(title = "放弃ABAC策略修改", businessType = BusinessType.DELETE)
    @DeleteMapping("/{groupCode}/draft")
    public R<PolicyGroupView> discardDraft(@PathVariable String groupCode) {
        return R.ok(groupService.discardDraft(groupCode));
    }

    @SaCheckPermission("platform:auth:policy:remove")
    @Log(title = "删除ABAC策略组", businessType = BusinessType.DELETE)
    @DeleteMapping("/{groupCode}")
    public R<Void> delete(@PathVariable String groupCode,
                          @Validated @RequestBody PolicyReasonCommand command) {
        groupService.delete(groupCode, command.getReason());
        return R.ok();
    }

    @SaCheckPermission("platform:auth:policy:remove")
    @Log(title = "恢复已删除ABAC策略组", businessType = BusinessType.UPDATE)
    @PutMapping("/{groupCode}/restore")
    public R<PolicyGroupView> restore(@PathVariable String groupCode) {
        return R.ok(groupService.restore(groupCode));
    }

    @SaCheckPermission("platform:auth:policy:query")
    @GetMapping("/{groupCode}/versions")
    public R<List<PolicyVersionView>> versions(@PathVariable String groupCode) {
        return R.ok(groupService.versions(groupCode));
    }

    @SaCheckPermission("platform:auth:policy:query")
    @GetMapping("/{groupCode}/operations")
    public R<List<PolicyOperationLogView>> operations(@PathVariable String groupCode) {
        return R.ok(groupService.operations(groupCode));
    }
}
