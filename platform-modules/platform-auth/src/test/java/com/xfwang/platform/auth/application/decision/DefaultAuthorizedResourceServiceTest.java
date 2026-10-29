package com.xfwang.platform.auth.application.decision;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xfwang.platform.api.auth.enums.PrincipalType;
import com.xfwang.platform.api.auth.model.AuthorizedResourceQuery;
import com.xfwang.platform.api.auth.model.AuthorizedResourceScope;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.api.auth.service.SubjectProvider;
import com.xfwang.platform.auth.domain.entity.AuthzGrant;
import com.xfwang.platform.auth.domain.entity.AuthzGrantScope;
import com.xfwang.platform.auth.domain.entity.AuthzResource;
import com.xfwang.platform.auth.domain.policy.PolicyConditionEvaluator;
import com.xfwang.platform.auth.domain.service.AdministratorAuthorizationPolicy;
import com.xfwang.platform.auth.domain.service.SubjectSelectorMatcher;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzGrantMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzGrantScopeMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzPolicyRuntimeResourceMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzPolicyRuntimeRuleMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzPolicyRuntimeSubjectMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzResourceMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultAuthorizedResourceServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void activeHumanGrantIsIncludedInAgentOwnerInheritedScope() {
        SubjectProvider subjectProvider = mock(SubjectProvider.class);
        AdministratorAuthorizationPolicy administrator = mock(AdministratorAuthorizationPolicy.class);
        AuthzResourceMapper resourceMapper = mock(AuthzResourceMapper.class);
        AuthzPolicyRuntimeRuleMapper ruleMapper = mock(AuthzPolicyRuntimeRuleMapper.class);
        AuthzPolicyRuntimeSubjectMapper subjectMapper = mock(AuthzPolicyRuntimeSubjectMapper.class);
        AuthzPolicyRuntimeResourceMapper runtimeResourceMapper = mock(AuthzPolicyRuntimeResourceMapper.class);
        AuthzGrantMapper grantMapper = mock(AuthzGrantMapper.class);
        AuthzGrantScopeMapper grantScopeMapper = mock(AuthzGrantScopeMapper.class);

        SubjectContext agent = SubjectContext.builder().tenantId("103")
            .principalType(PrincipalType.AGENT).principalId("agt_demo")
            .userId(1001L).build();
        when(subjectProvider.currentSubject()).thenReturn(agent);

        AuthzResource resource = new AuthzResource();
        resource.setResourceId(301L);
        resource.setTenantId("103");
        resource.setResourceType("govdata:datasource");
        resource.setStatus("ACTIVE");
        resource.setInheritPolicy(false);
        when(resourceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(resource));
        when(ruleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        AuthzGrant grant = new AuthzGrant();
        grant.setGrantId(401L);
        grant.setGranteeType("HUMAN");
        grant.setGranteeId("1001");
        grant.setPurpose("METADATA_INSPECTION");
        grant.setEffectiveTime(LocalDateTime.now().minusMinutes(1));
        grant.setStatus("ACTIVE");
        when(grantMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(grant));

        AuthzGrantScope grantScope = new AuthzGrantScope();
        grantScope.setGrantId(401L);
        grantScope.setResourceId(301L);
        grantScope.setActionCode("METADATA_INSPECT");
        grantScope.setIncludeChildren(false);
        when(grantScopeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(grantScope));

        DefaultAuthorizedResourceService service = new DefaultAuthorizedResourceService(
            subjectProvider, administrator, resourceMapper, ruleMapper, subjectMapper,
            runtimeResourceMapper, mock(PolicyConditionEvaluator.class), mock(AuthorizationJsonCodec.class),
            mock(RuntimePolicyRuleMatcher.class), new SubjectSelectorMatcher(), grantMapper, grantScopeMapper);

        AuthorizedResourceScope result = service.resolveScope(AuthorizedResourceQuery.builder()
            .resourceType("govdata:datasource")
            .action("METADATA_INSPECT")
            .purpose("METADATA_INSPECTION")
            .build());

        assertEquals(java.util.Set.of(301L), result.resourceIds());
    }
}
