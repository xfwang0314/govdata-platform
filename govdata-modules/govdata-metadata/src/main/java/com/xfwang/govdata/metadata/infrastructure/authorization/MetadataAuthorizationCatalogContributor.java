package com.xfwang.govdata.metadata.infrastructure.authorization;

import com.xfwang.govdata.api.metadata.MetadataObjectType;
import com.xfwang.govdata.metadata.domain.authorization.MetadataAuthorizationCodes;
import com.xfwang.platform.api.auth.catalog.AttributeOperandRole;
import com.xfwang.platform.api.auth.catalog.AttributeValueType;
import com.xfwang.platform.api.auth.catalog.AttributeVisibility;
import com.xfwang.platform.api.auth.catalog.AuthorizationActionDefinition;
import com.xfwang.platform.api.auth.catalog.AuthorizationAttributeDefinition;
import com.xfwang.platform.api.auth.catalog.AuthorizationCatalogContributor;
import com.xfwang.platform.api.auth.catalog.AuthorizationPurposeDefinition;
import com.xfwang.platform.api.auth.catalog.ResourceAuthorizationCapability;
import com.xfwang.platform.api.auth.catalog.ResourceNavigationCategory;
import com.xfwang.platform.api.auth.catalog.ResourceSelectionMode;
import com.xfwang.platform.api.auth.catalog.ResourceTypeDefinition;
import com.xfwang.platform.api.auth.enums.ConditionOperator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 元数据模块向统一授权中心贡献 Catalog、Schema、Table 层级资源。 */
@Component
public class MetadataAuthorizationCatalogContributor implements AuthorizationCatalogContributor {

    @Override public String moduleCode() { return MetadataAuthorizationCodes.MODULE; }
    @Override public String moduleLabel() { return "元数据目录"; }
    @Override public ResourceNavigationCategory navigationCategory() {
        return ResourceNavigationCategory.TECHNICAL_DATA_ASSET;
    }

    @Override
    public List<ResourceTypeDefinition> resourceTypes() {
        return List.of(
            resourceType(MetadataObjectType.CATALOG, "Catalog", false),
            resourceType(MetadataObjectType.SCHEMA, "Schema", false),
            resourceType(MetadataObjectType.TABLE, "表资产", true));
    }

    @Override
    public List<AuthorizationPurposeDefinition> purposes() {
        return List.of(
            new AuthorizationPurposeDefinition(MetadataAuthorizationCodes.Purpose.CATALOG, "目录浏览",
                Set.of(MetadataAuthorizationCodes.CATALOG_RESOURCE_TYPE,
                    MetadataAuthorizationCodes.SCHEMA_RESOURCE_TYPE,
                    MetadataAuthorizationCodes.TABLE_RESOURCE_TYPE)),
            new AuthorizationPurposeDefinition(MetadataAuthorizationCodes.Purpose.DATA_QUERY, "数据查询",
                Set.of(MetadataAuthorizationCodes.TABLE_RESOURCE_TYPE)));
    }

    private ResourceTypeDefinition resourceType(MetadataObjectType type, String label, boolean queryable) {
        Set<ConditionOperator> operators = Set.of(ConditionOperator.EQ, ConditionOperator.NE,
            ConditionOperator.IN, ConditionOperator.NOT_IN, ConditionOperator.EXISTS);
        List<AuthorizationActionDefinition> actions = new ArrayList<>(List.of(
            new AuthorizationActionDefinition(MetadataAuthorizationCodes.Action.VIEW, "查看目录", "LOW"),
            new AuthorizationActionDefinition(MetadataAuthorizationCodes.Action.GOVERN, "治理资产", "HIGH"),
            new AuthorizationActionDefinition(MetadataAuthorizationCodes.Action.AUTHORIZE, "共享授权", "CRITICAL")));
        if (queryable) {
            actions.add(1, new AuthorizationActionDefinition(MetadataAuthorizationCodes.Action.QUERY, "查询数据", "HIGH"));
        }
        return new ResourceTypeDefinition(type.getCode(), label, MetadataAuthorizationCodes.MODULE,
            MetadataAuthorizationCodes.resourceType(type), "元数据" + label, Set.of(),
            Set.of(ResourceSelectionMode.TYPE, ResourceSelectionMode.INSTANCE),
            Set.of(ResourceAuthorizationCapability.RESOURCE_CATALOG,
                ResourceAuthorizationCapability.CUSTOM_POLICY, ResourceAuthorizationCapability.GRANT,
                ResourceAuthorizationCapability.INSTANCE_DECISION), actions,
            List.of(
                attribute("resource.metadata.assetType", "资产类型", AttributeValueType.STRING, operators),
                attribute("resource.metadata.datasourceId", "数据源ID", AttributeValueType.INTEGER, operators),
                attribute("resource.metadata.revisionNo", "当前表修订号", AttributeValueType.INTEGER, operators),
                attribute("resource.metadata.status", "技术状态", AttributeValueType.STRING, operators)));
    }

    private AuthorizationAttributeDefinition attribute(String path, String label, AttributeValueType type,
                                                       Set<ConditionOperator> operators) {
        return new AuthorizationAttributeDefinition(path, label, type, operators, AttributeOperandRole.LEFT,
            AttributeVisibility.BASIC, List.of());
    }
}
