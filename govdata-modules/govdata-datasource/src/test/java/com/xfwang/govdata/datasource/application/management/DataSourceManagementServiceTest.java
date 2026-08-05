package com.xfwang.govdata.datasource.application.management;

import com.xfwang.govdata.datasource.domain.entity.DataSourceInstance;
import com.xfwang.govdata.datasource.infrastructure.persistence.mapper.DataSourceInstanceMapper;
import com.xfwang.govdata.datasource.infrastructure.connector.ConnectorCatalog;
import com.xfwang.platform.api.auth.model.ResourceDescriptor;
import com.xfwang.platform.api.auth.service.ResourceRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@Tag("dev")
class DataSourceManagementServiceTest {

    @Test
    void keepsAuthorizationResourceActiveWhenDatasourceIsDisabled() {
        ResourceRegistry registry = mock(ResourceRegistry.class);
        DataSourceInstance instance = instance("SUCCESS");
        instance.setStatus("DISABLED");
        DataSourceManagementService service = service(mock(DataSourceInstanceMapper.class), registry);

        ResourceDescriptor descriptor = service.toResourceDescriptor(instance);

        assertThat(descriptor.getStatus()).isEqualTo("ACTIVE");
        assertThat(descriptor.getAttributes()).containsEntry("datasourceStatus", "DISABLED");
    }

    @Test
    void refusesEnableBeforeSuccessfulConnectionTest() {
        DataSourceInstanceMapper instanceMapper = mock(DataSourceInstanceMapper.class);
        when(instanceMapper.selectById(1L)).thenReturn(instance("FAILED"));
        DataSourceManagementService service = service(instanceMapper, mock(ResourceRegistry.class));

        assertThatThrownBy(() -> service.changeStatus(1L, "ACTIVE"))
            .isInstanceOf(RuntimeException.class).hasMessageContaining("连接测试成功");
    }

    private DataSourceManagementService service(DataSourceInstanceMapper mapper, ResourceRegistry registry) {
        return new DataSourceManagementService(mapper, registry, mock(ConnectorCatalog.class));
    }

    private DataSourceInstance instance(String testStatus) {
        DataSourceInstance instance = new DataSourceInstance();
        instance.setDatasourceId(1L);
        instance.setAuthzResourceId(99L);
        instance.setDatasourceCode("ds_1");
        instance.setDatasourceName("测试数据源");
        instance.setConnectorCode("mysql");
        instance.setConnectorVersion("1.0");
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
