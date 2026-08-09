package com.xfwang.govdata.datasource.infrastructure.connector;

import com.xfwang.govdata.datasource.domain.exception.DataSourceErrorCode;
import com.xfwang.govdata.datasource.infrastructure.connector.registry.MetadataInspectorRegistry;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("元数据探测注册表测试")
class MetadataInspectorRegistryTest {

    private ConnectorMetadataInspector mysqlInspector;
    private MetadataInspectorRegistry registry;

    @BeforeEach
    void setUp() {
        mysqlInspector = mock(ConnectorMetadataInspector.class);
        when(mysqlInspector.supports("mysql", "1.0")).thenReturn(true);
        registry = new MetadataInspectorRegistry(List.of(mysqlInspector));
    }

    @Test
    @DisplayName("已知 Connector 能成功路由到 Inspector")
    void shouldReturnInspectorWhenSupported() {
        ConnectorMetadataInspector result = registry.require("mysql", "1.0");
        assertThat(result).isSameAs(mysqlInspector);
    }

    @Test
    @DisplayName("未知 Connector 触发异常")
    void shouldThrowExceptionWhenUnsupported() {
        assertThatThrownBy(() -> registry.require("oracle", "1.0"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("Connector 不支持元数据探测");
    }
}
