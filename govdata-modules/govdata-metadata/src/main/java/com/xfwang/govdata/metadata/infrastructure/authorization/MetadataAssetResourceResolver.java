package com.xfwang.govdata.metadata.infrastructure.authorization;

import com.xfwang.govdata.api.metadata.MetadataObjectType;
import com.xfwang.govdata.metadata.domain.entity.MetadataAsset;
import com.xfwang.govdata.metadata.domain.exception.MetadataErrorCode;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataAssetMapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

/** 将 Column 治理继承到所属 Table Resource，其余资产返回自身 Resource。 */
@Component("metadataAssetResourceResolver")
@RequiredArgsConstructor
public class MetadataAssetResourceResolver {
    private final MetadataAssetMapper assetMapper;

    public Long resolveResourceId(Long assetId) {
        MetadataAsset asset = assetId == null ? null : assetMapper.selectById(assetId);
        if (asset == null) throw new ServiceException(MetadataErrorCode.ASSET_NOT_FOUND);
        if (MetadataObjectType.COLUMN.getCode().equals(asset.getAssetType())) {
            asset = asset.getParentAssetId() == null ? null : assetMapper.selectById(asset.getParentAssetId());
        }
        if (asset == null || asset.getAuthzResourceId() == null) {
            throw new ServiceException(MetadataErrorCode.RESOURCE_PROJECTION_FAILED);
        }
        return asset.getAuthzResourceId();
    }
}
