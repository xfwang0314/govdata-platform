package com.xfwang.platform.auth.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xfwang.platform.auth.api.model.AccessDecision;
import com.xfwang.platform.auth.api.model.AccessRequest;
import com.xfwang.platform.auth.api.model.EnvironmentContext;
import com.xfwang.platform.auth.api.service.AuthorizationService;
import com.xfwang.platform.auth.application.model.AuthorizationCheckCommand;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/** 使用当前登录主体进行授权模拟和排障。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/platform/auth/decisions")
public class AuthzDecisionController {

    private final AuthorizationService authorizationService;

    @SaCheckPermission("platform:auth:decision:evaluate")
    @PostMapping("/evaluate")
    public R<AccessDecision> evaluate(@Validated @RequestBody AuthorizationCheckCommand command) {
        AccessRequest request = AccessRequest.builder()
            .resourceId(command.getResourceId())
            .resourceType(command.getResourceType())
            .action(command.getAction())
            .environment(EnvironmentContext.builder()
                .purpose(command.getPurpose())
                .requestTime(LocalDateTime.now())
                .build())
            .build();
        return R.ok(authorizationService.decide(request));
    }
}
