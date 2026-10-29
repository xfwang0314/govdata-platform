package com.xfwang.govdata.metadata.infrastructure.authorization;

import com.xfwang.govdata.api.metadata.MetadataObjectType;
import com.xfwang.govdata.metadata.application.authorization.event.MetadataAssetProjectionEvent;
import com.xfwang.govdata.metadata.domain.authorization.MetadataAuthorizationCodes;
import com.xfwang.govdata.metadata.domain.entity.MetadataAsset;
import com.xfwang.govdata.metadata.domain.enums.MetadataTechnicalStatus;
import com.xfwang.govdata.metadata.domain.exception.MetadataErrorCode;
import com.xfwang.govdata.metadata.infrastructure.persistence.mapper.MetadataAssetMapper;
import com.xfwang.platform.api.auth.enums.ResourceStatus;
import com.xfwang.platform.api.auth.model.ResourceDescriptor;
import com.xfwang.platform.api.auth.service.ResourceRegistry;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * 将 Catalog、Schema、Table 按父子关系投影到统一授权资源目录。
 *
 * <p>根级元数据资产挂到数据源 Resource；Column 不创建独立 Resource。</p>
 */
@Component
@RequiredArgsConstructor
public class MetadataAuthorizationProjectionHandler {

    private final ResourceRegistry resourceRegistry;
    private final MetadataAssetMapper assetMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void project(MetadataAssetProjectionEvent event) {
        MetadataObjectType type = objectType(event.assetType());
        Long parentResourceId = resolveParentResourceId(event);
        ResourceDescriptor resource = resourceRegistry.register(ResourceDescriptor.builder()
            .resourceName(event.assetName())
            .resourceType(MetadataAuthorizationCodes.resourceType(type))
            .parentId(parentResourceId)
            .businessId(String.valueOf(event.assetId()))
            .sourceModule(MetadataAuthorizationCodes.MODULE)
            .ownerDeptId(event.ownerDeptId())
            .classification(event.classification())
            .securityLevel(event.securityLevel())
            .status(MetadataTechnicalStatus.ACTIVE.getCode().equals(event.status())
                ? ResourceStatus.ACTIVE.code() : ResourceStatus.DISABLED.code())
            .attributes(Map.of("metadata", Map.of(
                "assetType", event.assetType(),
                "datasourceId", event.datasourceId(),
                "revisionNo", event.revisionNo() == null ? 0 : event.revisionNo(),
                "status", event.status())))
            .build());
        MetadataAsset projection = new MetadataAsset();
        projection.setAssetId(event.assetId());
        projection.setAuthzResourceId(resource.getResourceId());
        assetMapper.updateById(projection);
    }

    private Long resolveParentResourceId(MetadataAssetProjectionEvent event) {
        if (event.parentAssetId() == null) return event.datasourceResourceId();
        MetadataAsset parent = assetMapper.selectById(event.parentAssetId());
        if (parent == null) {
            throw new ServiceException(MetadataErrorCode.RESOURCE_PROJECTION_FAILED, "元数据授权父资产不存在");
        }
        if (parent.getAuthzResourceId() != null) return parent.getAuthzResourceId();
        MetadataObjectType parentType = objectType(parent.getAssetType());
        return resourceRegistry.findByBusinessKey(MetadataAuthorizationCodes.MODULE,
            MetadataAuthorizationCodes.resourceType(parentType), String.valueOf(parent.getAssetId()))
            .map(ResourceDescriptor::getResourceId)
            .orElseThrow(() -> new ServiceException(MetadataErrorCode.RESOURCE_PROJECTION_FAILED,
                "元数据授权父资源尚未投影"));
    }

    private MetadataObjectType objectType(String code) {
        for (MetadataObjectType value : MetadataObjectType.values()) {
            if (value.getCode().equals(code)) return value;
        }
        throw new ServiceException(MetadataErrorCode.RESOURCE_PROJECTION_FAILED,
            "元数据对象类型不支持授权投影");
    }
}
