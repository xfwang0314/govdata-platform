package com.xfwang.govdata.metadata.application.catalog;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xfwang.govdata.metadata.application.catalog.command.MetadataGovernanceCommand;
import com.xfwang.govdata.metadata.application.catalog.view.MetadataAssetView;
import com.xfwang.govdata.metadata.domain.authorization.MetadataAuthorizationCodes;
import com.xfwang.govdata.metadata.domain.entity.MetadataAsset;
import com.xfwang.govdata.metadata.domain.enums.MetadataGovernanceStatus;
import com.xfwang.govdata.metadata.domain.exception.MetadataErrorCode;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataAssetMapper;
import com.xfwang.platform.api.auth.annotation.ResourceAuthorize;
import com.xfwang.platform.api.auth.enums.DataClassification;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;

/** 人工治理属性维护；技术事实仍只能由采集写入。 */
@Service
@RequiredArgsConstructor
public class MetadataGovernanceService {
    private final MetadataAssetMapper assetMapper;
    private final MetadataCatalogQueryService queryService;

    @Transactional(rollbackFor = Exception.class)
    @ResourceAuthorize(resourceId = "@metadataAssetResourceResolver.resolveResourceId(#assetId)",
        action = MetadataAuthorizationCodes.Action.GOVERN, purpose = MetadataAuthorizationCodes.Purpose.CATALOG)
    public MetadataAssetView update(Long assetId, MetadataGovernanceCommand command) {
        MetadataAsset current = assetMapper.selectById(assetId);
        if (current == null) throw new ServiceException(MetadataErrorCode.ASSET_NOT_FOUND);
        if (!DataClassification.supports(command.getClassificationCode())
            || Arrays.stream(MetadataGovernanceStatus.values())
            .noneMatch(value -> value.getCode().equals(command.getGovernanceStatus()))) {
            throw new ServiceException(MetadataErrorCode.INVALID_GOVERNANCE);
        }
        int affected = assetMapper.update(null, new LambdaUpdateWrapper<MetadataAsset>()
            .eq(MetadataAsset::getAssetId, assetId)
            .eq(MetadataAsset::getLockVersion, command.getLockVersion())
            .set(MetadataAsset::getDisplayName, StringUtils.trimToNull(command.getDisplayName()))
            .set(MetadataAsset::getBusinessName, StringUtils.trimToNull(command.getBusinessName()))
            .set(MetadataAsset::getDescription, StringUtils.trimToNull(command.getDescription()))
            .set(MetadataAsset::getStewardUserId, command.getStewardUserId())
            .set(MetadataAsset::getClassificationCode, command.getClassificationCode())
            .set(MetadataAsset::getSecurityLevel, command.getSecurityLevel())
            .set(MetadataAsset::getGovernanceStatus, command.getGovernanceStatus())
            .set(MetadataAsset::getLockVersion, command.getLockVersion() + 1)
            .set(MetadataAsset::getUpdateTime, LocalDateTime.now()));
        if (affected != 1) throw new ServiceException(MetadataErrorCode.GOVERNANCE_CONFLICT);
        return queryService.getAsset(assetId);
    }
}
