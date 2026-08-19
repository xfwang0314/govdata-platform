package com.xfwang.govdata.datasource.infrastructure.authorization;

import com.xfwang.govdata.datasource.application.authorization.event.DataSourceAuthorizationProjectionEvent;
import com.xfwang.govdata.datasource.domain.entity.DataSourceInstance;
import com.xfwang.govdata.datasource.infrastructure.persistence.mapper.DataSourceInstanceMapper;
import com.xfwang.platform.api.auth.model.ResourceDescriptor;
import com.xfwang.platform.api.auth.service.ResourceRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class DataSourceAuthorizationProjectionHandlerTest {

    @Test
    void projectsOnlyRegisteredAuthorizationAttributesAndWritesBackResourceId() {
        ResourceRegistry registry = mock(ResourceRegistry.class);
        DataSourceInstanceMapper mapper = mock(DataSourceInstanceMapper.class);
        when(registry.register(any())).thenReturn(ResourceDescriptor.builder().resourceId(99L).build());
        DataSourceAuthorizationProjectionHandler handler =
            new DataSourceAuthorizationProjectionHandler(registry, mapper);

        handler.project(event(DataSourceAuthorizationProjectionEvent.Operation.UPSERT));

        ArgumentCaptor<ResourceDescriptor> descriptor = ArgumentCaptor.forClass(ResourceDescriptor.class);
        verify(registry).register(descriptor.capture());
        assertThat(descriptor.getValue().getResourceType()).isEqualTo("govdata:datasource");
        assertThat(descriptor.getValue().getBusinessId()).isEqualTo("1");
        assertThat(descriptor.getValue().getOwnerDeptId()).isEqualTo(103L);
        assertThat(descriptor.getValue().getAttributes()).containsOnlyKeys("datasource");
        verify(mapper).updateById(any(DataSourceInstance.class));
    }

    private DataSourceAuthorizationProjectionEvent event(
        DataSourceAuthorizationProjectionEvent.Operation operation) {
        return new DataSourceAuthorizationProjectionEvent(
            operation, 1L, null, "ds_1", "人口库", "mysql", "PROD", 103L, null,
            "READ_ONLY", "INTERNAL", 2, "ACTIVE");
    }
}
