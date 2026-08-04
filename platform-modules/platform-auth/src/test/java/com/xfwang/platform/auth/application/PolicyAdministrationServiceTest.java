package com.xfwang.platform.auth.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xfwang.platform.auth.application.model.PolicyDraftCommand;
import com.xfwang.platform.auth.application.model.PolicyResourceCommand;
import com.xfwang.platform.auth.application.model.PolicySubjectCommand;
import com.xfwang.platform.auth.application.model.PolicyView;
import com.xfwang.platform.auth.api.model.ResourceDescriptor;
import com.xfwang.platform.auth.api.service.ResourceRegistry;
import com.xfwang.platform.auth.decision.AuthorizationJsonCodec;
import com.xfwang.platform.auth.mapper.AuthzPolicyActionMapper;
import com.xfwang.platform.auth.mapper.AuthzPolicyMapper;
import com.xfwang.platform.auth.mapper.AuthzPolicyResourceMapper;
import com.xfwang.platform.auth.mapper.AuthzPolicySubjectMapper;
import com.xfwang.platform.auth.mapper.AuthzPolicyVersionMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("dev")
class PolicyAdministrationServiceTest {

    @Test
    void createsAndPublishesTheSameImmutablePolicyVersion() {
        AuthzPolicyMapper policyMapper = mock(AuthzPolicyMapper.class);
        AuthzPolicyVersionMapper versionMapper = mock(AuthzPolicyVersionMapper.class);
        AuthzPolicySubjectMapper subjectMapper = mock(AuthzPolicySubjectMapper.class);
        AuthzPolicyResourceMapper resourceMapper = mock(AuthzPolicyResourceMapper.class);
        AuthzPolicyActionMapper actionMapper = mock(AuthzPolicyActionMapper.class);
        ResourceRegistry resourceRegistry = mock(ResourceRegistry.class);

        AtomicReference<com.xfwang.platform.auth.domain.AuthzPolicy> storedPolicy = new AtomicReference<>();
        AtomicReference<com.xfwang.platform.auth.domain.AuthzPolicyVersion> storedVersion = new AtomicReference<>();
        doAnswer(invocation -> {
            com.xfwang.platform.auth.domain.AuthzPolicy value = invocation.getArgument(0);
            value.setPolicyId(101L);
            storedPolicy.set(value);
            return 1;
        }).when(policyMapper).insert(any(com.xfwang.platform.auth.domain.AuthzPolicy.class));
        doAnswer(invocation -> {
            com.xfwang.platform.auth.domain.AuthzPolicyVersion value = invocation.getArgument(0);
            value.setPolicyVersionId(201L);
            storedVersion.set(value);
            return 1;
        }).when(versionMapper).insert(any(com.xfwang.platform.auth.domain.AuthzPolicyVersion.class));
        when(policyMapper.selectById(101L)).thenAnswer(invocation -> storedPolicy.get());
        when(versionMapper.selectOne(any())).thenAnswer(invocation -> storedVersion.get());
        when(resourceRegistry.findById(301L)).thenReturn(Optional.of(
            ResourceDescriptor.builder().resourceId(301L).build()));

        PolicyAdministrationService service = new DefaultPolicyAdministrationService(
            policyMapper, versionMapper, subjectMapper, resourceMapper, actionMapper,
            new AuthorizationJsonCodec(new ObjectMapper()), resourceRegistry);

        PolicyDraftCommand command = command();
        PolicyView draft = service.createDraft(command);

        assertThat(draft.getPolicyId()).isEqualTo(101L);
        assertThat(draft.getPolicyVersionId()).isEqualTo(201L);
        assertThat(draft.getStatus()).isEqualTo("DRAFT");
        assertThat(draft.getVersionNo()).isEqualTo(1);

        PolicyView published = service.publish(101L);

        assertThat(published.getStatus()).isEqualTo("PUBLISHED");
        assertThat(published.getPolicyVersionId()).isEqualTo(201L);
        assertThat(storedPolicy.get().getCurrentVersionId()).isEqualTo(201L);
        assertThat(storedVersion.get().getVersionStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    void rejectsInvalidConditionJsonBeforePersistingDraft() {
        AuthzPolicyMapper policyMapper = mock(AuthzPolicyMapper.class);
        PolicyAdministrationService service = new DefaultPolicyAdministrationService(
            policyMapper,
            mock(AuthzPolicyVersionMapper.class),
            mock(AuthzPolicySubjectMapper.class),
            mock(AuthzPolicyResourceMapper.class),
            mock(AuthzPolicyActionMapper.class),
            new AuthorizationJsonCodec(new ObjectMapper()),
            mock(ResourceRegistry.class));
        PolicyDraftCommand command = command();
        command.setConditionJson("{not-json}");

        assertThatThrownBy(() -> service.createDraft(command))
            .hasMessageContaining("策略条件或约束不是合法 JSON");
    }

    private PolicyDraftCommand command() {
        PolicySubjectCommand subject = new PolicySubjectCommand();
        subject.setSubjectType("ROLE");
        subject.setSubjectId("data_user");

        PolicyResourceCommand resource = new PolicyResourceCommand();
        resource.setResourceId(301L);

        PolicyDraftCommand command = new PolicyDraftCommand();
        command.setPolicyCode("population-query");
        command.setPolicyName("人口数据查询策略");
        command.setEffect("PERMIT");
        command.setConditionJson("{\"logic\":\"ALL\",\"conditions\":[],\"groups\":[]}");
        command.setSubjects(List.of(subject));
        command.setResources(List.of(resource));
        command.setActions(Set.of("QUERY"));
        return command;
    }
}
