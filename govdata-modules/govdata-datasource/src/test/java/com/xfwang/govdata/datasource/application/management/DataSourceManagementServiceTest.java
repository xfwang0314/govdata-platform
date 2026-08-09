package com.xfwang.govdata.datasource.application.management;

import com.xfwang.govdata.datasource.domain.entity.DataSourceInstance;
import com.xfwang.govdata.datasource.domain.connector.ConnectorAuthProfile;
import com.xfwang.govdata.datasource.application.management.command.DataSourceCommand;
import com.xfwang.govdata.datasource.infrastructure.connector.ConnectorCatalog;
import com.xfwang.govdata.datasource.infrastructure.persistence.mapper.DataSourceInstanceMapper;
import com.xfwang.platform.api.secret.SecretCatalog;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class DataSourceManagementServiceTest {

    @Test
    void refusesEnableBeforeSuccessfulConnectionTest() {
        DataSourceInstanceMapper instanceMapper = mock(DataSourceInstanceMapper.class);
        when(instanceMapper.selectById(1L)).thenReturn(instance("FAILED"));
        DataSourceManagementService service = new DataSourceManagementService(instanceMapper,
            mock(ConnectorCatalog.class), mock(SecretCatalog.class));

        assertThatThrownBy(() -> service.changeStatus(1L, "ACTIVE"))
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
            instanceMapper, connectorCatalog, secretCatalog);

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
