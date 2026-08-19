package com.xfwang.platform.auth.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xfwang.platform.api.auth.model.AccessDecision;
import com.xfwang.platform.api.auth.model.AccessRequest;
import com.xfwang.platform.api.auth.model.EnvironmentContext;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.api.auth.service.AuthorizationService;
import com.xfwang.platform.auth.application.decision.command.AuthorizationCheckCommand;
import com.xfwang.platform.auth.application.decision.DecisionLogQueryService;
import com.xfwang.platform.auth.application.decision.query.DecisionLogQuery;
import com.xfwang.platform.auth.application.decision.view.DecisionLogView;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final DecisionLogQueryService decisionLogQueryService;

    @SaCheckPermission("platform:auth:decision:query")
    @GetMapping("/logs/page")
    public R<PageResult<DecisionLogView>> logs(DecisionLogQuery query, PageQuery pageQuery) {
        return R.ok(decisionLogQueryService.page(query, pageQuery));
    }

    @SaCheckPermission("platform:auth:decision:evaluate")
    @PostMapping("/evaluate")
    public R<AccessDecision> evaluate(@Validated @RequestBody AuthorizationCheckCommand command) {
        SubjectContext targetSubject = null;
        String subjectType = command.getSubjectType();
        String subjectId = command.getSubjectId();
        if (subjectId == null && command.getUserId() != null) {
            subjectType = "HUMAN";
            subjectId = String.valueOf(command.getUserId());
        }

        if (subjectType != null && subjectId != null) {
            String tenantId = org.dromara.common.satoken.utils.LoginHelper.getTenantId();
            if ("HUMAN".equalsIgnoreCase(subjectType)) {
                Long uid = Long.valueOf(subjectId);
                targetSubject = SubjectContext.builder()
                    .tenantId(tenantId)
                    .principalType(com.xfwang.platform.api.auth.enums.PrincipalType.HUMAN)
                    .principalId(subjectId)
                    .userId(uid)
                    .deptId(null)
                    .deptPathIds(java.util.Set.of())
                    .roleIds(java.util.Set.of())
                    .roleKeys(java.util.Set.of())
                    .attributes(java.util.Map.of())
                    .build();
            } else if ("DEPARTMENT".equalsIgnoreCase(subjectType)) {
                Long deptId = Long.valueOf(subjectId);
                targetSubject = SubjectContext.builder()
                    .tenantId(tenantId)
                    .principalType(com.xfwang.platform.api.auth.enums.PrincipalType.HUMAN)
                    .principalId(subjectId)
                    .userId(null)
                    .deptId(deptId)
                    .deptPathIds(java.util.Set.of(deptId))
                    .roleIds(java.util.Set.of())
                    .roleKeys(java.util.Set.of())
                    .attributes(java.util.Map.of())
                    .build();
            } else if ("ROLE".equalsIgnoreCase(subjectType)) {
                Long roleId = Long.valueOf(subjectId);
                targetSubject = SubjectContext.builder()
                    .tenantId(tenantId)
                    .principalType(com.xfwang.platform.api.auth.enums.PrincipalType.HUMAN)
                    .principalId(subjectId)
                    .userId(null)
                    .deptId(null)
                    .deptPathIds(java.util.Set.of())
                    .roleIds(java.util.Set.of(roleId))
                    .roleKeys(java.util.Set.of())
                    .attributes(java.util.Map.of())
                    .build();
            }
        }

        AccessRequest request = AccessRequest.builder()
            .resourceId(command.getResourceId())
            .resourceType(command.getResourceType())
            .action(command.getAction())
            .subject(targetSubject)
            .environment(EnvironmentContext.builder()
                .purpose(command.getPurpose())
                .requestTime(LocalDateTime.now())
                .build())
            .build();
        return R.ok(authorizationService.decide(request));
    }
}
