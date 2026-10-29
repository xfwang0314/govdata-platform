package com.xfwang.govdata.metadata.harvest.infrastructure.authorization;

import com.xfwang.govdata.metadata.harvest.domain.authorization.HarvestAuthorizationCodes;
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

import java.util.List;
import java.util.Set;

/** 采集计划资源类型及管理动作目录。 */
@Component
public class HarvestAuthorizationCatalogContributor implements AuthorizationCatalogContributor {
    @Override public String moduleCode() { return HarvestAuthorizationCodes.MODULE; }
    @Override public String moduleLabel() { return "元数据采集"; }
    @Override public ResourceNavigationCategory navigationCategory() {
        return ResourceNavigationCategory.DATA_GOVERNANCE_OPERATION;
    }
    @Override public List<ResourceTypeDefinition> resourceTypes() {
        Set<ConditionOperator> equality = Set.of(ConditionOperator.EQ, ConditionOperator.NE,
            ConditionOperator.IN, ConditionOperator.NOT_IN, ConditionOperator.EXISTS);
        return List.of(new ResourceTypeDefinition("HARVEST_PLAN", "采集计划", HarvestAuthorizationCodes.MODULE,
            HarvestAuthorizationCodes.RESOURCE_TYPE, "采集计划", Set.of(),
            Set.of(ResourceSelectionMode.TYPE, ResourceSelectionMode.INSTANCE),
            Set.of(ResourceAuthorizationCapability.RESOURCE_CATALOG,
                ResourceAuthorizationCapability.CUSTOM_POLICY, ResourceAuthorizationCapability.GRANT,
                ResourceAuthorizationCapability.INSTANCE_DECISION),
            List.of(new AuthorizationActionDefinition(HarvestAuthorizationCodes.ACTION_VIEW, "查看", "LOW"),
                new AuthorizationActionDefinition(HarvestAuthorizationCodes.ACTION_MANAGE, "管理", "HIGH"),
                new AuthorizationActionDefinition(HarvestAuthorizationCodes.ACTION_EXECUTE, "执行", "HIGH")),
            List.of(attribute("resource.harvestPlan.scheduleType", "调度类型", equality),
                attribute("resource.harvestPlan.status", "计划状态", equality))));
    }
    @Override public List<AuthorizationPurposeDefinition> purposes() {
        return List.of(new AuthorizationPurposeDefinition(HarvestAuthorizationCodes.PURPOSE, "采集计划管理",
            Set.of(HarvestAuthorizationCodes.RESOURCE_TYPE)));
    }
    private AuthorizationAttributeDefinition attribute(String path, String label, Set<ConditionOperator> operators) {
        return new AuthorizationAttributeDefinition(path, label, AttributeValueType.STRING, operators,
            AttributeOperandRole.LEFT, AttributeVisibility.BASIC, List.of());
    }
}
