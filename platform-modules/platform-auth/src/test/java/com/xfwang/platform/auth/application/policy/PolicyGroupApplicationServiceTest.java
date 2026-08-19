package com.xfwang.platform.auth.application.policy;

import com.xfwang.platform.api.auth.service.SubjectProvider;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.auth.application.decision.AuthorizationJsonCodec;
import com.xfwang.platform.auth.domain.entity.AuthzPolicyGroup;
import com.xfwang.platform.auth.domain.entity.AuthzPolicyOperationLog;
import com.xfwang.platform.auth.domain.entity.AuthzPolicyVersion;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzPolicyGroupMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzPolicyOperationLogMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzPolicyRuntimeResourceMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzPolicyRuntimeRuleMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzPolicyRuntimeSubjectMapper;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzPolicyVersionMapper;
import com.xfwang.platform.auth.domain.service.PolicyLifecycle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyGroupApplicationServiceTest {

    @Mock
    private AuthzPolicyGroupMapper groupMapper;
    @Mock
    private AuthzPolicyVersionMapper versionMapper;
    @Mock
    private AuthzPolicyRuntimeRuleMapper runtimeRuleMapper;
    @Mock
    private AuthzPolicyRuntimeSubjectMapper runtimeSubjectMapper;
    @Mock
    private AuthzPolicyRuntimeResourceMapper runtimeResourceMapper;
    @Mock
    private AuthzPolicyOperationLogMapper operationLogMapper;
    @Mock
    private PolicyDocumentCodec documentCodec;
    @Mock
    private AuthorizationPolicySchemaValidator schemaValidator;
    @Mock
    private AuthorizationJsonCodec jsonCodec;
    @Mock
    private SubjectProvider subjectProvider;
    @Spy
    private PolicyLifecycle lifecycle = new PolicyLifecycle();

    @InjectMocks
    private PolicyGroupApplicationService service;

    @Test
    void rejectsCreatingNextVersionWhilePublishedVersionIsRunning() {
        AuthzPolicyGroup group = group("ACTIVE", 10L, null);
        when(groupMapper.selectOne(any())).thenReturn(group);

        assertThatThrownBy(() -> service.createNextVersion("pgrp_running"))
            .hasMessageContaining("不允许")
            .hasMessageContaining("CREATE_VERSION");
    }

    @Test
    void rejectsEnablingOrDeletingWhilePendingVersionExists() {
        AuthzPolicyGroup group = group("DISABLED", 10L, 11L);
        when(groupMapper.selectOne(any())).thenReturn(group);

        assertThatThrownBy(() -> service.enable("pgrp_pending"))
            .hasMessageContaining("待发布版本");
        assertThatThrownBy(() -> service.delete("pgrp_pending", "测试删除"))
            .hasMessageContaining("放弃修改");
    }

    @Test
    void marksPendingVersionDiscardedAndKeepsCurrentVersionDisabled() {
        AuthzPolicyGroup group = group("DISABLED", 10L, 11L);
        group.setPolicyGroupId(1L);
        group.setPolicyGroupCode("pgrp_pending");
        AuthzPolicyVersion current = version(10L, "PUBLISHED");
        AuthzPolicyVersion pending = version(11L, "DRAFT");
        when(groupMapper.selectOne(any())).thenReturn(group);
        when(versionMapper.selectById(anyLong())).thenAnswer(invocation ->
            Long.valueOf(11L).equals(invocation.getArgument(0)) ? pending : current);
        when(versionMapper.updateById(any(AuthzPolicyVersion.class))).thenReturn(1);
        when(groupMapper.updateById(any(AuthzPolicyGroup.class))).thenReturn(1);
        when(operationLogMapper.insert(any(AuthzPolicyOperationLog.class)))
            .thenReturn(1);
        when(subjectProvider.currentSubject()).thenReturn(SubjectContext.builder()
            .tenantId("000000").userId(1L).deptId(103L).build());

        service.discardDraft("pgrp_pending");

        assertThat(pending.getVersionStatus()).isEqualTo("DISCARDED");
        assertThat(group.getDraftVersionId()).isNull();
        assertThat(group.getCurrentVersionId()).isEqualTo(10L);
        assertThat(group.getStatus()).isEqualTo("DISABLED");
    }

    @Test
    void treatsLegacySharedCurrentAndPendingPointerAsUnpublishedPolicy() {
        AuthzPolicyGroup group = group("DISABLED", 11L, 11L);
        group.setPolicyGroupId(1L);
        group.setPolicyGroupCode("pgrp_legacy_pending");
        AuthzPolicyVersion pending = version(11L, "DRAFT");
        when(groupMapper.selectOne(any())).thenReturn(group);
        when(versionMapper.selectById(11L)).thenReturn(pending);
        when(groupMapper.updateById(any(AuthzPolicyGroup.class))).thenReturn(1);
        when(operationLogMapper.insert(any(AuthzPolicyOperationLog.class))).thenReturn(1);
        when(subjectProvider.currentSubject()).thenReturn(SubjectContext.builder()
            .tenantId("000000").userId(1L).deptId(103L).build());

        service.discardDraft("pgrp_legacy_pending");

        assertThat(group.getDelFlag()).isEqualTo("1");
        assertThat(group.getCurrentVersionId()).isNull();
        assertThat(group.getStatus()).isEqualTo("DRAFT");
        assertThat(pending.getVersionStatus()).isEqualTo("DRAFT");
    }

    @Test
    void doesNotExposeDiscardedVersionAsPendingVersion() {
        AuthzPolicyGroup group = group("DRAFT", null, 11L);
        group.setPolicyGroupId(1L);
        group.setPolicyGroupCode("pgrp_discarded_pointer");
        when(groupMapper.selectOne(any())).thenReturn(group);
        when(versionMapper.selectById(11L)).thenReturn(version(11L, "DISCARDED"));

        var view = service.get("pgrp_discarded_pointer");

        assertThat(view.isHasDraft()).isFalse();
        assertThat(view.getDraftVersionNo()).isNull();
        assertThat(view.getDraftVersion()).isNull();
    }

    private AuthzPolicyGroup group(String status, Long currentVersionId, Long pendingVersionId) {
        AuthzPolicyGroup group = new AuthzPolicyGroup();
        group.setStatus(status);
        group.setCurrentVersionId(currentVersionId);
        group.setDraftVersionId(pendingVersionId);
        return group;
    }

    private AuthzPolicyVersion version(Long id, String status) {
        AuthzPolicyVersion version = new AuthzPolicyVersion();
        version.setPolicyGroupVersionId(id);
        version.setVersionStatus(status);
        version.setContentHash("hash-" + id);
        return version;
    }

    private static final class ValidationReached extends RuntimeException {
    }
}
