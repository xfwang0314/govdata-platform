package com.xfwang.platform.auth.decision;

import com.xfwang.platform.auth.api.enums.DecisionType;
import com.xfwang.platform.auth.api.model.AccessDecision;
import com.xfwang.platform.auth.api.model.AccessRequest;
import com.xfwang.platform.auth.api.model.ResourceDescriptor;
import com.xfwang.platform.auth.api.model.SubjectContext;
import com.xfwang.platform.auth.api.service.AuthorizationService;
import com.xfwang.platform.auth.api.service.ResourceRegistry;
import com.xfwang.platform.auth.api.service.SubjectProvider;
import com.xfwang.platform.auth.domain.AuthzPolicy;
import com.xfwang.platform.auth.domain.AuthzPolicyAction;
import com.xfwang.platform.auth.domain.AuthzPolicyResource;
import com.xfwang.platform.auth.domain.AuthzPolicySubject;
import com.xfwang.platform.auth.domain.AuthzPolicyVersion;
import com.xfwang.platform.auth.domain.AuthzGrant;
import com.xfwang.platform.auth.domain.AuthzGrantScope;
import com.xfwang.platform.auth.domain.AuthzResource;
import com.xfwang.platform.auth.mapper.AuthzDecisionLogMapper;
import com.xfwang.platform.auth.mapper.AuthzGrantMapper;
import com.xfwang.platform.auth.mapper.AuthzGrantScopeMapper;
import com.xfwang.platform.auth.mapper.AuthzPolicyActionMapper;
import com.xfwang.platform.auth.mapper.AuthzPolicyMapper;
import com.xfwang.platform.auth.mapper.AuthzPolicyResourceMapper;
import com.xfwang.platform.auth.mapper.AuthzPolicySubjectMapper;
import com.xfwang.platform.auth.mapper.AuthzPolicyVersionMapper;
import com.xfwang.platform.auth.mapper.AuthzResourceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("dev")
class DefaultAuthorizationServiceTest {

    private final SubjectProvider subjectProvider = mock(SubjectProvider.class);
    private final ResourceRegistry resourceRegistry = mock(ResourceRegistry.class);
    private final AuthzResourceMapper resourceMapper = mock(AuthzResourceMapper.class);
    private final AuthzPolicyMapper policyMapper = mock(AuthzPolicyMapper.class);
    private final AuthzPolicyVersionMapper policyVersionMapper = mock(AuthzPolicyVersionMapper.class);
    private final AuthzPolicySubjectMapper policySubjectMapper = mock(AuthzPolicySubjectMapper.class);
    private final AuthzPolicyResourceMapper policyResourceMapper = mock(AuthzPolicyResourceMapper.class);
    private final AuthzPolicyActionMapper policyActionMapper = mock(AuthzPolicyActionMapper.class);
    private final AuthzGrantMapper grantMapper = mock(AuthzGrantMapper.class);
    private final AuthzGrantScopeMapper grantScopeMapper = mock(AuthzGrantScopeMapper.class);
    private final AuthzDecisionLogMapper decisionLogMapper = mock(AuthzDecisionLogMapper.class);

    private AuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        authorizationService = new DefaultAuthorizationService(
            subjectProvider,
            resourceRegistry,
            resourceMapper,
            policyMapper,
            policyVersionMapper,
            policySubjectMapper,
            policyResourceMapper,
            policyActionMapper,
            grantMapper,
            grantScopeMapper,
            decisionLogMapper,
            new com.xfwang.platform.auth.policy.PolicyConditionEvaluator(),
            new AuthorizationObligationMerger(),
            new AuthorizationJsonCodec(new ObjectMapper()));
        when(decisionLogMapper.insert(any(com.xfwang.platform.auth.domain.AuthzDecisionLog.class))).thenReturn(1);
        when(grantMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void publishedPolicyVersionPermitsMatchingHumanResourceAndAction() {
        SubjectContext subject = SubjectContext.builder()
            .tenantId("100001")
            .userId(10L)
            .deptId(20L)
            .roleIds(Set.of(30L))
            .build();
        when(subjectProvider.currentSubject()).thenReturn(subject);

        ResourceDescriptor descriptor = ResourceDescriptor.builder()
            .resourceId(100L)
            .tenantId("100001")
            .resourceCode("govdata:metadata:table:100")
            .resourceType("govdata:table")
            .status("ACTIVE")
            .build();
        when(resourceRegistry.findById(100L)).thenReturn(Optional.of(descriptor));

        AuthzResource resource = new AuthzResource();
        resource.setResourceId(100L);
        resource.setInheritPolicy(false);
        when(resourceMapper.selectById(100L)).thenReturn(resource);

        AuthzPolicyResource resourceBinding = new AuthzPolicyResource();
        resourceBinding.setPolicyVersionId(200L);
        resourceBinding.setResourceId(100L);
        when(policyResourceMapper.selectList(any())).thenReturn(List.of(resourceBinding));

        AuthzPolicy policy = new AuthzPolicy();
        policy.setPolicyId(150L);
        policy.setCurrentVersionId(200L);
        policy.setStatus("PUBLISHED");
        when(policyMapper.selectList(any())).thenReturn(List.of(policy));

        AuthzPolicyVersion version = new AuthzPolicyVersion();
        version.setPolicyVersionId(200L);
        version.setPolicyId(150L);
        version.setEffect("PERMIT");
        version.setConditionJson("{\"logic\":\"ALL\",\"conditions\":[],\"groups\":[]}");
        version.setVersionStatus("PUBLISHED");
        when(policyVersionMapper.selectList(any())).thenReturn(List.of(version));

        AuthzPolicySubject subjectBinding = new AuthzPolicySubject();
        subjectBinding.setPolicyVersionId(200L);
        subjectBinding.setSubjectType("HUMAN");
        subjectBinding.setSubjectId("10");
        when(policySubjectMapper.selectList(any())).thenReturn(List.of(subjectBinding));

        AuthzPolicyAction actionBinding = new AuthzPolicyAction();
        actionBinding.setPolicyVersionId(200L);
        actionBinding.setActionCode("QUERY");
        when(policyActionMapper.selectList(any())).thenReturn(List.of(actionBinding));

        AccessDecision decision = authorizationService.decide(AccessRequest.builder()
            .resourceId(100L)
            .resourceType("govdata:table")
            .action("QUERY")
            .build());

        assertThat(decision.getDecision()).isEqualTo(DecisionType.PERMIT);
        assertThat(decision.getMatchedPolicyIds()).containsExactly(200L);
    }

    @Test
    void invalidGrantObligationCannotPermitAccess() {
        SubjectContext subject = SubjectContext.builder()
            .tenantId("100001")
            .userId(10L)
            .build();
        when(subjectProvider.currentSubject()).thenReturn(subject);

        ResourceDescriptor descriptor = ResourceDescriptor.builder()
            .resourceId(100L)
            .tenantId("100001")
            .resourceCode("govdata:metadata:table:100")
            .resourceType("govdata:table")
            .status("ACTIVE")
            .build();
        when(resourceRegistry.findById(100L)).thenReturn(Optional.of(descriptor));

        AuthzResource resource = new AuthzResource();
        resource.setResourceId(100L);
        resource.setInheritPolicy(false);
        when(resourceMapper.selectById(100L)).thenReturn(resource);
        when(policyResourceMapper.selectList(any())).thenReturn(List.of());

        AuthzGrant grant = new AuthzGrant();
        grant.setGrantId(300L);
        grant.setGranteeType("HUMAN");
        grant.setGranteeId("10");
        grant.setStatus("ACTIVE");
        grant.setEffectiveTime(LocalDateTime.now().minusHours(1));
        when(grantMapper.selectList(any())).thenReturn(List.of(grant));

        AuthzGrantScope scope = new AuthzGrantScope();
        scope.setScopeId(400L);
        scope.setGrantId(300L);
        scope.setResourceId(100L);
        scope.setActionCode("QUERY");
        scope.setObligationJson("{not-json}");
        when(grantScopeMapper.selectList(any())).thenReturn(List.of(scope));

        AccessDecision decision = authorizationService.decide(AccessRequest.builder()
            .resourceId(100L)
            .resourceType("govdata:table")
            .action("QUERY")
            .build());

        assertThat(decision.getDecision()).isEqualTo(DecisionType.DENY);
        assertThat(decision.getReasonCode()).isEqualTo("AUTHZ_DEFAULT_DENY");
        assertThat(decision.getMatchedGrantIds()).isEmpty();
    }
}
