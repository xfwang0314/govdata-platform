package com.xfwang.govdata.metadata.application.impact;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xfwang.govdata.api.metadata.MetadataObjectType;
import com.xfwang.govdata.metadata.application.catalog.MetadataCatalogQueryService;
import com.xfwang.govdata.metadata.application.impact.view.MetadataImpactView;
import com.xfwang.govdata.metadata.domain.entity.MetadataAsset;
import com.xfwang.govdata.metadata.domain.entity.MetadataImpactProjection;
import com.xfwang.govdata.metadata.domain.exception.MetadataErrorCode;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataAssetMapper;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataImpactProjectionMapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;

/** 在授权可见范围内查询由下游模块投影的依赖影响。 */
@Service
@RequiredArgsConstructor
public class MetadataImpactQueryService {
    private final MetadataAssetMapper assetMapper;
    private final MetadataImpactProjectionMapper projectionMapper;
    private final MetadataCatalogQueryService catalogQueryService;

    public PageResult<MetadataImpactView> pageByAsset(Long assetId, PageQuery pageQuery) {
        MetadataAsset asset = assetId == null ? null : assetMapper.selectById(assetId);
        if (asset == null) throw new ServiceException(MetadataErrorCode.ASSET_NOT_FOUND);
        Long tableAssetId = MetadataObjectType.TABLE.getCode().equals(asset.getAssetType())
            ? asset.getAssetId() : MetadataObjectType.COLUMN.getCode().equals(asset.getAssetType())
                ? asset.getParentAssetId() : null;
        if (tableAssetId == null) return PageResult.build(java.util.List.of(), 0L);
        catalogQueryService.requireVisibleTable(tableAssetId);
        Page<MetadataImpactProjection> page = projectionMapper.selectPage(pageQuery.build(),
            new LambdaQueryWrapper<MetadataImpactProjection>()
                .eq(MetadataImpactProjection::getTableAssetId, tableAssetId)
                .orderByAsc(MetadataImpactProjection::getSourceModule)
                .orderByAsc(MetadataImpactProjection::getConsumerType)
                .orderByAsc(MetadataImpactProjection::getConsumerName)
                .orderByAsc(MetadataImpactProjection::getImpactProjectionId));
        return PageResult.build(page.getRecords().stream().map(this::toView).toList(), page.getTotal());
    }

    private MetadataImpactView toView(MetadataImpactProjection value) {
        return MetadataImpactView.builder().impactProjectionId(value.getImpactProjectionId())
            .sourceModule(value.getSourceModule()).consumerType(value.getConsumerType())
            .consumerId(value.getConsumerId()).consumerCode(value.getConsumerCode())
            .consumerName(value.getConsumerName()).consumerStatus(value.getConsumerStatus())
            .assetId(value.getAssetId()).tableAssetId(value.getTableAssetId())
            .dependencyType(value.getDependencyType()).sourceUpdatedTime(value.getSourceUpdatedTime()).build();
    }
}
