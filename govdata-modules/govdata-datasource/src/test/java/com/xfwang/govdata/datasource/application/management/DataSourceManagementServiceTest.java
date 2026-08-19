package com.xfwang.govdata.datasource.application.management;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xfwang.govdata.datasource.domain.entity.DataSourceInstance;
import com.xfwang.govdata.datasource.domain.connector.ConnectorAuthProfile;
import com.xfwang.govdata.datasource.application.management.command.DataSourceCommand;
import com.xfwang.govdata.datasource.infrastructure.connector.ConnectorCatalog;
import com.xfwang.govdata.datasource.infrastructure.persistence.mapper.DataSourceInstanceMapper;
import com.xfwang.platform.api.secret.SecretCatalog;
import com.xfwang.platform.api.auth.service.AuthorizedResourceService;
import com.xfwang.govdata.datasource.application.authorization.DataSourceAuthorizationScope;
import com.xfwang.govdata.datasource.application.management.query.DataSourceQuery;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class DataSourceManagementServiceTest {

    @Test
    void returnsEmptyPageWithoutQueryingDatasourceWhenSubjectHasNoVisibleResource() {
        DataSourceInstanceMapper instanceMapper = mock(DataSourceInstanceMapper.class);
        AuthorizedResourceService authorization = mock(AuthorizedResourceService.class);
        when(authorization.resolveScope(any())).thenReturn(
            com.xfwang.platform.api.auth.model.AuthorizedResourceScope.restricted(java.util.Set.of()));
        DataSourceAuthorizationScope authorizationScope = new DataSourceAuthorizationScope(authorization);
        DataSourceManagementService service = new DataSourceManagementService(instanceMapper,
            mock(ConnectorCatalog.class), mock(SecretCatalog.class), event -> { }, authorizationScope);

        var result = service.page(new DataSourceQuery(), new PageQuery(20, 1));

        assertThat(result.getRows()).isEmpty();
        assertThat(result.getTotal()).isZero();
        verify(instanceMapper, never()).selectPage(any(), any());
    }

    @Test
    void administratorUnrestrictedScopeDoesNotGenerateResourceIdInClause() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), "test"),
            DataSourceInstance.class);
        DataSourceInstanceMapper instanceMapper = mock(DataSourceInstanceMapper.class);
        AuthorizedResourceService authorization = mock(AuthorizedResourceService.class);
        when(authorization.resolveScope(any())).thenReturn(
            com.xfwang.platform.api.auth.model.AuthorizedResourceScope.unrestrictedScope());
        Page<DataSourceInstance> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(1L);
        when(instanceMapper.selectPage(any(), any())).thenReturn(page);
        DataSourceManagementService service = new DataSourceManagementService(instanceMapper,
            mock(ConnectorCatalog.class), mock(SecretCatalog.class), event -> { },
            new DataSourceAuthorizationScope(authorization));

        var result = service.page(new DataSourceQuery(), new PageQuery(20, 1));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<DataSourceInstance>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(instanceMapper).selectPage(any(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).doesNotContain("authz_resource_id");
        assertThat(result.getTotal()).isEqualTo(1L);
    }

    @Test
    void refusesEnableBeforeSuccessfulConnectionTest() {
        DataSourceInstanceMapper instanceMapper = mock(DataSourceInstanceMapper.class);
        when(instanceMapper.selectById(1L)).thenReturn(instance("FAILED"));
        DataSourceManagementService service = new DataSourceManagementService(instanceMapper,
            mock(ConnectorCatalog.class), mock(SecretCatalog.class), event -> { },
            mock(DataSourceAuthorizationScope.class));

        assertThatThrownBy(() -> service.enable(1L))
            .isInstanceOf(RuntimeException.class).hasMessageContaining("连接测试成功");
    }

    @Test
    void allowsConnectorWithoutAuthenticationAndDoesNotResolveSecret() {
        DataSourceInstanceMapper instanceMapper = mock(DataSourceInstanceMapper.class);
        ConnectorCatalog connectorCatalog = mock(ConnectorCatalog.class);
        SecretCatalog secretCatalog = mock(SecretCatalog.class);
        ConnectorAuthProfile none = ConnectorAuthProfile.builder()
            .profileCode("NONE").profileName("无认证").build();
        DataSourceCommand command = new DataSourceCommand();
        command.setSecretRef(null);
        DataSourceManagementService service = new DataSourceManagementService(
            instanceMapper, connectorCatalog, secretCatalog, event -> { },
            mock(DataSourceAuthorizationScope.class));

        service.validateSecretReference(command, none);

        verify(secretCatalog, never()).validateReference(any());
    }

    private DataSourceInstance instance(String testStatus) {
        DataSourceInstance instance = new DataSourceInstance();
        instance.setDatasourceId(1L);
        instance.setDatasourceCode("ds_1");
        instance.setDatasourceName("测试数据源");
        instance.setConnectorCode("mysql");
        instance.setConnectorVersion("1.0");
        instance.setAuthProfileCode("PASSWORD");
        instance.setEndpointConfigJson("{}");
        instance.setSecretRef("secret:sec_1");
        instance.setEnvironment("DEV");
        instance.setOwnerDeptId(103L);
        instance.setAccessMode("READ_ONLY");
        instance.setClassificationCode("INTERNAL");
        instance.setSecurityLevel(1);
        instance.setStatus("DISABLED");
        instance.setLastTestStatus(testStatus);
        instance.setLockVersion(0);
        return instance;
    }
}
