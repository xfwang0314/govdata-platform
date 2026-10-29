package com.xfwang.govdata.datasource.infrastructure.authorization;

import com.xfwang.govdata.datasource.domain.authorization.DataSourceAuthorizationCodes;
import com.xfwang.govdata.datasource.domain.enums.DataSourceAuthorizationPurpose;
import com.xfwang.govdata.datasource.domain.enums.DataSourceResourceAction;
import com.xfwang.platform.api.auth.catalog.AttributeValueType;
import com.xfwang.platform.api.auth.catalog.AuthorizationActionDefinition;
import com.xfwang.platform.api.auth.catalog.AuthorizationAttributeDefinition;
import com.xfwang.platform.api.auth.catalog.AuthorizationCatalogContributor;
import com.xfwang.platform.api.auth.catalog.AuthorizationPurposeDefinition;
import com.xfwang.platform.api.auth.catalog.ResourceTypeDefinition;
import com.xfwang.platform.api.auth.catalog.ResourceSelectionMode;
import com.xfwang.platform.api.auth.catalog.ResourceAuthorizationCapability;
import com.xfwang.platform.api.auth.catalog.ResourceNavigationCategory;
import com.xfwang.platform.api.auth.catalog.AttributeOperandRole;
import com.xfwang.platform.api.auth.catalog.AttributeVisibility;
import com.xfwang.platform.api.auth.catalog.AuthorizationValueOption;
import com.xfwang.govdata.datasource.domain.enums.DataSourceEnums;
import com.xfwang.platform.api.auth.enums.ConditionOperator;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** 数据源模块向通用授权目录贡献 Resource Schema。 */
@Component
public class DataSourceAuthorizationCatalogContributor implements AuthorizationCatalogContributor {

    @Override
    public String moduleCode() {
        return DataSourceAuthorizationCodes.MODULE;
    }

    @Override
    public String moduleLabel() {
        return "数据源管理";
    }

    @Override
    public ResourceNavigationCategory navigationCategory() {
        return ResourceNavigationCategory.TECHNICAL_DATA_ASSET;
    }

    @Override
    public List<ResourceTypeDefinition> resourceTypes() {
        Set<ConditionOperator> equality = Set.of(ConditionOperator.EQ, ConditionOperator.NE,
            ConditionOperator.IN, ConditionOperator.NOT_IN, ConditionOperator.EXISTS);
        return List.of(new ResourceTypeDefinition(
            "CONNECTION", "连接资源", DataSourceAuthorizationCodes.MODULE,
            DataSourceAuthorizationCodes.RESOURCE_TYPE, "数据源", Set.of(),
            Set.of(ResourceSelectionMode.TYPE, ResourceSelectionMode.INSTANCE),
            Set.of(ResourceAuthorizationCapability.RESOURCE_CATALOG,
                ResourceAuthorizationCapability.CUSTOM_POLICY,
                ResourceAuthorizationCapability.GRANT,
                ResourceAuthorizationCapability.INSTANCE_DECISION),
            Arrays.stream(DataSourceResourceAction.values())
                .map(value -> new AuthorizationActionDefinition(
                    value.code(), value.label(), value.riskLevel()))
                .toList(),
            List.of(
                attribute("resource.datasource.environment", "数据源环境", equality,
                    Arrays.stream(DataSourceEnums.Environment.values()).toList()),
                attribute("resource.datasource.accessMode", "访问模式", equality,
                    Arrays.stream(DataSourceEnums.AccessMode.values()).toList()),
                attribute("resource.datasource.status", "数据源业务状态", equality,
                    Arrays.stream(DataSourceEnums.Status.values()).toList()),
                attribute("resource.datasource.connectorCode", "Connector 编码", equality))));
    }

    @Override
    public List<AuthorizationPurposeDefinition> purposes() {
        return Arrays.stream(DataSourceAuthorizationPurpose.values())
            .map(value -> new AuthorizationPurposeDefinition(value.code(), value.label(),
                Set.of(DataSourceAuthorizationCodes.RESOURCE_TYPE)))
            .toList();
    }

    private AuthorizationAttributeDefinition attribute(String path, String label,
                                                       Set<ConditionOperator> operators) {
        return new AuthorizationAttributeDefinition(path, label, AttributeValueType.STRING, operators,
            AttributeOperandRole.LEFT, AttributeVisibility.BASIC, List.of());
    }

    private AuthorizationAttributeDefinition attribute(String path, String label,
                                                       Set<ConditionOperator> operators,
                                                       List<? extends DataSourceEnums.Coded> values) {
        return new AuthorizationAttributeDefinition(path, label, AttributeValueType.STRING, operators,
            AttributeOperandRole.LEFT, AttributeVisibility.BASIC,
            values.stream().map(value -> new AuthorizationValueOption(value.label(), value.code())).toList());
    }
}
