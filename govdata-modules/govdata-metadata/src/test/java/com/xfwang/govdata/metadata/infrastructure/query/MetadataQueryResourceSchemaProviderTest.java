package com.xfwang.govdata.metadata.infrastructure.query;

import com.xfwang.govdata.api.query.spi.QueryResourceSchema;
import com.xfwang.govdata.metadata.domain.entity.MetadataAsset;
import com.xfwang.govdata.metadata.domain.entity.MetadataColumn;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataAssetMapper;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataColumnMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetadataQueryResourceSchemaProviderTest {

    @Test
    void resolvesCurrentSchemaByStableAssets() {
        MetadataAssetMapper assetMapper = mock(MetadataAssetMapper.class);
        MetadataColumnMapper columnMapper = mock(MetadataColumnMapper.class);
        MetadataQueryResourceSchemaProvider provider = new MetadataQueryResourceSchemaProvider(assetMapper, columnMapper);

        MetadataAsset table = asset(10L, "TABLE", "orders", 9L);
        table.setDatasourceId(20L);
        table.setConnectorCode("mysql");
        table.setCurrentRevisionNo(5);
        MetadataAsset schema = asset(9L, "SCHEMA", "public", 8L);
        MetadataAsset catalog = asset(8L, "CATALOG", "government", null);
        MetadataAsset columnAsset = asset(11L, "COLUMN", "id", 10L);
        columnAsset.setAssetCode("mda_abc");
        columnAsset.setDisplayName("主键");
        when(assetMapper.selectOne(any())).thenReturn(table);
        when(assetMapper.selectList(any())).thenReturn(List.of(columnAsset));
        when(assetMapper.selectById(9L)).thenReturn(schema);
        when(assetMapper.selectById(8L)).thenReturn(catalog);

        MetadataColumn column = new MetadataColumn();
        column.setAssetId(11L);
        column.setOrdinalPosition(1);
        when(columnMapper.selectByIds(any())).thenReturn(List.of(column));

        Optional<QueryResourceSchema> resolved = provider.findByResourceId(99L);

        assertThat(resolved).isPresent();
        assertThat(resolved.orElseThrow().getCatalogName()).isEqualTo("government");
        assertThat(resolved.orElseThrow().getSchemaName()).isEqualTo("public");
        assertThat(resolved.orElseThrow().getObjectName()).isEqualTo("orders");
        assertThat(resolved.orElseThrow().getCurrentRevisionNo()).isEqualTo(5);
        assertThat(resolved.orElseThrow().getFields()).singleElement().satisfies(field -> {
            assertThat(field.getFieldId()).isEqualTo(11L);
            assertThat(field.getFieldCode()).isEqualTo("mda_abc");
            assertThat(field.getPhysicalName()).isEqualTo("id");
        });
    }

    private MetadataAsset asset(Long id, String type, String name, Long parentId) {
        MetadataAsset value = new MetadataAsset();
        value.setAssetId(id);
        value.setAssetType(type);
        value.setOriginalName(name);
        value.setParentAssetId(parentId);
        value.setTechnicalStatus("ACTIVE");
        return value;
    }
}
