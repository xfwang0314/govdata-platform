package com.xfwang.platform.auth.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xfwang.platform.auth.application.policy.PolicyAdministrationService;
import com.xfwang.platform.auth.application.policy.command.PolicyDraftCommand;
import com.xfwang.platform.auth.application.policy.view.PolicyView;
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

/** Policy 生命周期管理入口。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/platform/auth/policies")
public class AuthzPolicyController {

    private final PolicyAdministrationService policyService;

    @SaCheckPermission("platform:auth:policy:add")
    @Log(title = "ABAC策略", businessType = BusinessType.INSERT)
    @PostMapping
    public R<PolicyView> create(@Validated @RequestBody PolicyDraftCommand command) {
        return R.ok(policyService.createDraft(command));
    }

    @SaCheckPermission("platform:auth:policy:publish")
    @Log(title = "发布ABAC策略", businessType = BusinessType.UPDATE)
    @PutMapping("/{policyId}/publish")
    public R<PolicyView> publish(@PathVariable Long policyId) {
        return R.ok(policyService.publish(policyId));
    }

    @SaCheckPermission("platform:auth:policy:query")
    @GetMapping("/{policyId}")
    public R<PolicyView> get(@PathVariable Long policyId) {
        return R.ok(policyService.get(policyId));
    }
}
