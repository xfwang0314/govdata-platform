package com.xfwang.govdata.metadata.infrastructure.query;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xfwang.govdata.api.metadata.MetadataObjectType;
import com.xfwang.govdata.api.query.spi.QueryFieldSchema;
import com.xfwang.govdata.api.query.spi.QueryResourceSchema;
import com.xfwang.govdata.api.query.spi.QueryResourceSchemaProvider;
import com.xfwang.govdata.metadata.domain.entity.MetadataAsset;
import com.xfwang.govdata.metadata.domain.entity.MetadataColumn;
import com.xfwang.govdata.metadata.domain.enums.MetadataTechnicalStatus;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataAssetMapper;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataColumnMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 从可信当前状态解析查询网关所需的物理 Schema。 */
@Component
@RequiredArgsConstructor
public class MetadataQueryResourceSchemaProvider implements QueryResourceSchemaProvider {

    private final MetadataAssetMapper assetMapper;
    private final MetadataColumnMapper columnMapper;

    @Override
    public Optional<QueryResourceSchema> findByResourceId(Long resourceId) {
        if (resourceId == null) return Optional.empty();
        MetadataAsset table = assetMapper.selectOne(new LambdaQueryWrapper<MetadataAsset>()
            .eq(MetadataAsset::getAuthzResourceId, resourceId)
            .eq(MetadataAsset::getAssetType, MetadataObjectType.TABLE.getCode()));
        if (table == null || !List.of(MetadataTechnicalStatus.ACTIVE.getCode(), MetadataTechnicalStatus.OUT_OF_SCOPE.getCode())
            .contains(table.getTechnicalStatus())) return Optional.empty();

        List<MetadataAsset> columnAssets = assetMapper.selectList(new LambdaQueryWrapper<MetadataAsset>()
            .eq(MetadataAsset::getParentAssetId, table.getAssetId())
            .eq(MetadataAsset::getAssetType, MetadataObjectType.COLUMN.getCode())
            .in(MetadataAsset::getTechnicalStatus, MetadataTechnicalStatus.ACTIVE.getCode(), MetadataTechnicalStatus.OUT_OF_SCOPE.getCode()));
        Map<Long, MetadataAsset> assetsById = columnAssets.stream()
            .collect(Collectors.toMap(MetadataAsset::getAssetId, Function.identity()));
        List<MetadataColumn> columns = columnAssets.isEmpty() ? List.of()
            : columnMapper.selectByIds(assetsById.keySet()).stream()
                .sorted(java.util.Comparator.comparing(MetadataColumn::getOrdinalPosition)
                    .thenComparing(MetadataColumn::getAssetId)).toList();
        NamespacePath path = namespacePath(table.getParentAssetId());
        List<QueryFieldSchema> fields = columns.stream().map(column -> {
            MetadataAsset asset = assetsById.get(column.getAssetId());
            return QueryFieldSchema.builder()
                .fieldId(asset.getAssetId())
                .fieldCode(asset.getAssetCode())
                .displayName(asset.getDisplayName() == null ? asset.getOriginalName() : asset.getDisplayName())
                .authorizationAttribute("field." + asset.getAssetCode())
                .physicalName(asset.getOriginalName())
                .selectable(true)
                .build();
        }).toList();
        return Optional.of(QueryResourceSchema.builder()
            .resourceId(resourceId)
            .datasourceId(table.getDatasourceId())
            .connectorCode(table.getConnectorCode())
            .catalogName(path.catalogName())
            .schemaName(path.schemaName())
            .objectName(table.getOriginalName())
            .currentRevisionNo(table.getCurrentRevisionNo())
            .fields(fields)
            .build());
    }

    private NamespacePath namespacePath(Long parentAssetId) {
        String catalog = null;
        String schema = null;
        Long currentId = parentAssetId;
        for (int depth = 0; depth < 2 && currentId != null; depth++) {
            MetadataAsset parent = assetMapper.selectById(currentId);
            if (parent == null) break;
            if (MetadataObjectType.CATALOG.getCode().equals(parent.getAssetType())) catalog = parent.getOriginalName();
            if (MetadataObjectType.SCHEMA.getCode().equals(parent.getAssetType())) schema = parent.getOriginalName();
            currentId = parent.getParentAssetId();
        }
        return new NamespacePath(catalog, schema);
    }

    private record NamespacePath(String catalogName, String schemaName) { }
}
