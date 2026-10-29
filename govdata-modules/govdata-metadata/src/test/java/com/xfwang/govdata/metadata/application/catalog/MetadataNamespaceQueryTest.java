package com.xfwang.govdata.metadata.application.catalog;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.xfwang.govdata.api.metadata.MetadataObjectType;
import com.xfwang.govdata.metadata.application.authorization.MetadataAuthorizationScope;
import com.xfwang.govdata.metadata.domain.entity.MetadataAsset;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataAssetMapper;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataColumnMapper;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataTableMapper;
import com.xfwang.platform.api.auth.model.AuthorizedResourceScope;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("dev")
class MetadataNamespaceQueryTest {
    private final MetadataAssetMapper mapper = mock(MetadataAssetMapper.class);
    private final MetadataAuthorizationScope authorization = mock(MetadataAuthorizationScope.class);
    private final MetadataCatalogQueryService service = new MetadataCatalogQueryService(mapper,
        mock(MetadataTableMapper.class), mock(MetadataColumnMapper.class), authorization);

    @BeforeEach
    void prepare() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), MetadataAsset.class);
    }

    @Test
    void namespaceEndpointDataReturnsVisibleCatalogAndSchemaWithoutTableNodes() {
        MetadataAsset catalog = asset(10L, null, "CATALOG", null);
        MetadataAsset schema = asset(11L, 10L, "SCHEMA", null);
        MetadataAsset table = asset(12L, 11L, "TABLE", 900L);
        when(mapper.selectList(any())).thenReturn(List.of(catalog, schema, table));
        when(authorization.catalogViewScope(MetadataObjectType.CATALOG))
            .thenReturn(AuthorizedResourceScope.restricted(Set.of()));
        when(authorization.catalogViewScope(MetadataObjectType.SCHEMA))
            .thenReturn(AuthorizedResourceScope.restricted(Set.of()));
        when(authorization.catalogViewScope(MetadataObjectType.TABLE))
            .thenReturn(AuthorizedResourceScope.restricted(Set.of(900L)));

        var result = service.namespaceTree(1L);

        assertThat(result).extracting("assetId").containsExactly(10L);
        assertThat(result.get(0).getChildren()).extracting("assetId").containsExactly(11L);
        assertThat(result.get(0).getChildren().get(0).getChildren()).isEmpty();
    }

    private MetadataAsset asset(Long id, Long parentId, String type, Long resourceId) {
        MetadataAsset asset = new MetadataAsset();
        asset.setAssetId(id);
        asset.setParentAssetId(parentId);
        asset.setDatasourceId(1L);
        asset.setAssetType(type);
        asset.setAuthzResourceId(resourceId);
        return asset;
    }
}
