package com.xfwang.govdata.metadata.application.impact;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.xfwang.govdata.api.metadata.MetadataImpactBatch;
import com.xfwang.govdata.api.metadata.MetadataImpactDependency;
import com.xfwang.govdata.api.metadata.MetadataImpactRegistry;
import com.xfwang.govdata.api.metadata.MetadataObjectType;
import com.xfwang.govdata.metadata.domain.entity.MetadataAsset;
import com.xfwang.govdata.metadata.domain.entity.MetadataImpactProjection;
import com.xfwang.govdata.metadata.domain.exception.MetadataErrorCode;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataAssetMapper;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataImpactProjectionMapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.tenant.helper.TenantHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 下游依赖投影写入 Adapter；消费者事实仍由来源模块拥有。 */
@Service
@RequiredArgsConstructor
public class MetadataImpactRegistryService implements MetadataImpactRegistry {
    private static final int MAX_DEPENDENCIES = 500;

    private final MetadataAssetMapper assetMapper;
    private final MetadataImpactProjectionMapper projectionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replace(MetadataImpactBatch batch) {
        validateHeader(batch);
        List<MetadataImpactDependency> dependencies = batch.getDependencies() == null
            ? List.of() : List.copyOf(batch.getDependencies());
        if (dependencies.size() > MAX_DEPENDENCIES) invalid();
        Set<Long> assetIds = dependencies.stream().map(MetadataImpactDependency::getAssetId)
            .collect(Collectors.toSet());
        if (assetIds.size() != dependencies.size() || assetIds.contains(null)) invalid();
        Map<Long, MetadataAsset> assets = assetIds.isEmpty() ? Map.of()
            : assetMapper.selectByIds(assetIds).stream()
                .collect(Collectors.toMap(MetadataAsset::getAssetId, Function.identity()));
        if (assets.size() != assetIds.size()) throw new ServiceException(MetadataErrorCode.ASSET_NOT_FOUND);
        validateDependencies(dependencies, assets);

        projectionMapper.delete(new LambdaQueryWrapper<MetadataImpactProjection>()
            .eq(MetadataImpactProjection::getSourceModule, batch.getSourceModule())
            .eq(MetadataImpactProjection::getConsumerType, batch.getConsumerType())
            .eq(MetadataImpactProjection::getConsumerId, batch.getConsumerId()));
        if (dependencies.isEmpty()) return;
        List<MetadataImpactProjection> projections = dependencies.stream().map(value -> projection(batch, value))
            .sorted(java.util.Comparator.comparing(MetadataImpactProjection::getImpactProjectionId)).toList();
        projectionMapper.insertBatch(projections, MAX_DEPENDENCIES);
    }

    private void validateHeader(MetadataImpactBatch batch) {
        if (batch == null || !TenantHelper.getTenantId().equals(batch.getTenantId())
            || StringUtils.isBlank(batch.getSourceModule()) || batch.getSourceModule().length() > 64
            || StringUtils.isBlank(batch.getConsumerType()) || batch.getConsumerType().length() > 64
            || StringUtils.isBlank(batch.getConsumerId()) || batch.getConsumerId().length() > 100) {
            invalid();
        }
    }

    private void validateDependencies(List<MetadataImpactDependency> dependencies, Map<Long, MetadataAsset> assets) {
        Set<String> unique = new HashSet<>();
        for (MetadataImpactDependency value : dependencies) {
            MetadataAsset asset = assets.get(value.getAssetId());
            boolean table = MetadataObjectType.TABLE.getCode().equals(asset.getAssetType());
            boolean column = MetadataObjectType.COLUMN.getCode().equals(asset.getAssetType());
            Long expectedTableId = table ? asset.getAssetId() : column ? asset.getParentAssetId() : null;
            if (expectedTableId == null || !expectedTableId.equals(value.getTableAssetId())
                || StringUtils.isBlank(value.getDependencyType()) || value.getDependencyType().length() > 64
                || StringUtils.isBlank(value.getConsumerName()) || value.getConsumerName().length() > 200
                || StringUtils.isBlank(value.getConsumerStatus()) || value.getConsumerStatus().length() > 32
                || value.getConsumerCode() != null && value.getConsumerCode().length() > 64
                || !unique.add(value.getAssetId() + "|" + value.getDependencyType())) {
                invalid();
            }
        }
    }

    private MetadataImpactProjection projection(MetadataImpactBatch batch, MetadataImpactDependency source) {
        MetadataImpactProjection value = new MetadataImpactProjection();
        value.setImpactProjectionId(IdWorker.getId());
        value.setSourceModule(batch.getSourceModule());
        value.setConsumerType(batch.getConsumerType());
        value.setConsumerId(batch.getConsumerId());
        value.setConsumerCode(StringUtils.trimToNull(source.getConsumerCode()));
        value.setConsumerName(source.getConsumerName().trim());
        value.setConsumerStatus(source.getConsumerStatus());
        value.setAssetId(source.getAssetId());
        value.setTableAssetId(source.getTableAssetId());
        value.setDependencyType(source.getDependencyType());
        value.setSourceUpdatedTime(source.getSourceUpdatedTime());
        return value;
    }

    private void invalid() {
        throw new ServiceException(MetadataErrorCode.INVALID_IMPACT_PROJECTION);
    }
}
