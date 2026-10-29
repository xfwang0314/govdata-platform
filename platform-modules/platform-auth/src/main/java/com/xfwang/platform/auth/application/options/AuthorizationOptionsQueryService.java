package com.xfwang.platform.auth.application.options;

import com.xfwang.platform.api.auth.catalog.AuthorizationAttributeDefinition;
import com.xfwang.platform.api.auth.catalog.ResourceAuthorizationCapability;
import com.xfwang.platform.api.auth.catalog.ResourceTypeDefinition;
import com.xfwang.platform.api.auth.enums.ConditionOperator;
import com.xfwang.platform.api.auth.enums.DataClassification;
import com.xfwang.platform.api.auth.enums.GrantSourceType;
import com.xfwang.platform.api.auth.enums.GrantStatus;
import com.xfwang.platform.api.auth.enums.DecisionType;
import com.xfwang.platform.api.auth.enums.PolicyRiskLevel;
import com.xfwang.platform.api.auth.enums.PolicyAggregateStatus;
import com.xfwang.platform.api.auth.enums.PolicyVersionStatus;
import com.xfwang.platform.api.auth.enums.ResourceStatus;
import com.xfwang.platform.api.auth.enums.SecurityLevel;
import com.xfwang.platform.api.auth.enums.SubjectType;
import com.xfwang.platform.auth.application.catalog.AuthorizationCatalog;
import com.xfwang.platform.auth.application.options.view.AuthzOptionsView;
import com.xfwang.platform.auth.application.options.view.AuthzOptionsView.AttributeItem;
import com.xfwang.platform.auth.application.options.view.AuthzOptionsView.OptionItem;
import com.xfwang.platform.auth.application.options.view.AuthzOptionsView.ResourceTypeItem;
import com.xfwang.platform.auth.application.options.view.AuthzOptionsView.ResourceCategoryItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 将贡献式授权目录转换为页面可消费的稳定 Schema。 */
@Service
@RequiredArgsConstructor
public class AuthorizationOptionsQueryService {

    private final AuthorizationCatalog catalog;

    public AuthzOptionsView options() {
        java.util.LinkedHashMap<String, OptionItem> domains = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, OptionItem> modules = new java.util.LinkedHashMap<>();
        List<ResourceTypeItem> resourceTypes = catalog.resourceTypes().stream()
            .peek(type -> domains.putIfAbsent(type.domainCode(), item(type.domainLabel(), type.domainCode())))
            .peek(type -> modules.putIfAbsent(type.sourceModule(),
                item(catalog.moduleLabel(type.sourceModule()), type.sourceModule())))
            .map(this::resourceType)
            .toList();
        return AuthzOptionsView.builder()
            .resourceDomains(List.copyOf(domains.values()))
            .resourceModules(List.copyOf(modules.values()))
            .resourceCategories(catalog.navigationCategories().stream()
                .map(value -> ResourceCategoryItem.builder()
                    .code(value.code()).label(value.label()).order(value.order()).build())
                .toList())
            .resourceTypes(resourceTypes)
            .resourceStatuses(java.util.Arrays.stream(ResourceStatus.values())
                .map(value -> item(value.label(), value.code())).toList())
            .classifications(java.util.Arrays.stream(DataClassification.values())
                .map(value -> item(value.label(), value.code())).toList())
            .securityLevels(java.util.Arrays.stream(SecurityLevel.values())
                .map(value -> item(value.label(), String.valueOf(value.level()))).toList())
            .policyEffects(java.util.Arrays.stream(DecisionType.values())
                .map(value -> item(value.label(), value.code())).toList())
            .policyRiskLevels(java.util.Arrays.stream(PolicyRiskLevel.values())
                .map(value -> item(value.label(), value.code())).toList())
            .policyStatuses(java.util.Arrays.stream(PolicyAggregateStatus.values())
                .map(value -> item(value.label(), value.code())).toList())
            .policyVersionStatuses(java.util.Arrays.stream(PolicyVersionStatus.values())
                .map(value -> item(value.label(), value.code())).toList())
            .grantStatuses(java.util.Arrays.stream(GrantStatus.values())
                .map(value -> item(value.label(), value.code())).toList())
            .grantSourceTypes(java.util.Arrays.stream(GrantSourceType.values())
                .map(value -> item(value.label(), value.code())).toList())
            .grantPurposes(catalog.purposes().stream()
                .filter(definition -> definition.resourceTypeCodes().stream()
                    .anyMatch(typeCode -> catalog.findResourceType(typeCode)
                        .map(type -> type.capabilities().contains(ResourceAuthorizationCapability.GRANT))
                        .orElse(false)))
                .map(value -> item(value.label(), value.code())).toList())
            .subjectTypes(java.util.Arrays.stream(SubjectType.values())
                .map(value -> item(subjectLabel(value), value.name())).toList())
            .operators(java.util.Arrays.stream(ConditionOperator.values())
                .map(value -> item(value.label(), value.code())).toList())
            .build();
    }

    private ResourceTypeItem resourceType(ResourceTypeDefinition definition) {
        return ResourceTypeItem.builder()
            .domainCode(definition.domainCode())
            .domainLabel(definition.domainLabel())
            .sourceModule(definition.sourceModule())
            .moduleLabel(catalog.moduleLabel(definition.sourceModule()))
            .categoryCode(catalog.navigationCategory(definition.sourceModule()).code())
            .typeCode(definition.typeCode())
            .typeLabel(definition.typeLabel())
            .selectionModes(definition.selectionModes().stream().map(Enum::name).sorted().toList())
            .capabilities(definition.capabilities().stream().map(Enum::name).sorted().toList())
            .actions(definition.actions().stream()
                .map(value -> item(value.label(), value.code())).toList())
            .purposes(catalog.purposesFor(definition.typeCode()).stream()
                .map(value -> item(value.label(), value.code())).toList())
            .attributes(catalog.attributesFor(definition.typeCode()).stream()
                .map(this::attribute).toList())
            .build();
    }

    private AttributeItem attribute(AuthorizationAttributeDefinition definition) {
        return AttributeItem.builder()
            .path(definition.path())
            .label(definition.label())
            .valueType(definition.valueType().name())
            .operators(definition.operators().stream().map(Enum::name).sorted().toList())
            .operandRole(definition.operandRole().name())
            .visibility(definition.visibility().name())
            .valueOptions(definition.valueOptions().stream()
                .map(value -> item(value.label(), value.value())).toList())
            .build();
    }

    private String subjectLabel(SubjectType type) {
        return switch (type) {
            case ANY -> "所有主体";
            case HUMAN -> "指定用户";
            case ROLE -> "角色";
            case DEPARTMENT -> "部门";
            case SERVICE -> "服务身份";
            case AGENT -> "Agent 身份";
        };
    }

    private OptionItem item(String label, String value) {
        return OptionItem.builder().label(label).value(value).build();
    }
}
