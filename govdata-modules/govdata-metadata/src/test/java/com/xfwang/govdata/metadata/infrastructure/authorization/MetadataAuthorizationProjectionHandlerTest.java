package com.xfwang.govdata.metadata.infrastructure.authorization;

import com.xfwang.govdata.metadata.application.authorization.event.MetadataAssetProjectionEvent;
import com.xfwang.govdata.metadata.domain.entity.MetadataAsset;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataAssetMapper;
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
class MetadataAuthorizationProjectionHandlerTest {

    @Test
    void projectsRootCatalogBelowDatasourceResource() {
        ResourceRegistry registry = mock(ResourceRegistry.class);
        MetadataAssetMapper mapper = mock(MetadataAssetMapper.class);
        when(registry.register(any())).thenReturn(ResourceDescriptor.builder().resourceId(201L).build());
        MetadataAuthorizationProjectionHandler handler = new MetadataAuthorizationProjectionHandler(registry, mapper);

        handler.project(event(11L, "CATALOG", null, 100L));

        ArgumentCaptor<ResourceDescriptor> descriptor = ArgumentCaptor.forClass(ResourceDescriptor.class);
        verify(registry).register(descriptor.capture());
        assertThat(descriptor.getValue().getResourceType()).isEqualTo("govdata:metadata:catalog");
        assertThat(descriptor.getValue().getParentId()).isEqualTo(100L);
        verify(mapper).updateById(any(MetadataAsset.class));
    }

    @Test
    void projectsTableBelowParentMetadataResource() {
        ResourceRegistry registry = mock(ResourceRegistry.class);
        MetadataAssetMapper mapper = mock(MetadataAssetMapper.class);
        MetadataAsset parent = new MetadataAsset();
        parent.setAssetId(12L);
        parent.setAssetType("SCHEMA");
        parent.setAuthzResourceId(202L);
        when(mapper.selectById(12L)).thenReturn(parent);
        when(registry.register(any())).thenReturn(ResourceDescriptor.builder().resourceId(203L).build());
        MetadataAuthorizationProjectionHandler handler = new MetadataAuthorizationProjectionHandler(registry, mapper);

        handler.project(event(13L, "TABLE", 12L, 100L));

        ArgumentCaptor<ResourceDescriptor> descriptor = ArgumentCaptor.forClass(ResourceDescriptor.class);
        verify(registry).register(descriptor.capture());
        assertThat(descriptor.getValue().getResourceType()).isEqualTo("govdata:metadata:table");
        assertThat(descriptor.getValue().getParentId()).isEqualTo(202L);
    }

    private MetadataAssetProjectionEvent event(Long assetId, String type, Long parentAssetId,
                                               Long datasourceResourceId) {
        return new MetadataAssetProjectionEvent(assetId, type, parentAssetId, datasourceResourceId,
            "asset", 103L, "INTERNAL", 2, 1L, 10, "ACTIVE");
    }
}
