package com.xfwang.platform.auth.application.catalog;

import com.xfwang.platform.api.auth.catalog.AttributeValueType;
import com.xfwang.platform.api.auth.catalog.AuthorizationActionDefinition;
import com.xfwang.platform.api.auth.catalog.AuthorizationAttributeDefinition;
import com.xfwang.platform.api.auth.catalog.AuthorizationCatalogContributor;
import com.xfwang.platform.api.auth.catalog.AuthorizationPurposeDefinition;
import com.xfwang.platform.api.auth.catalog.ResourceTypeDefinition;
import com.xfwang.platform.api.auth.catalog.ResourceNavigationCategory;
import com.xfwang.platform.api.auth.catalog.AttributeOperandRole;
import com.xfwang.platform.api.auth.catalog.AttributeVisibility;
import com.xfwang.platform.api.auth.catalog.AuthorizationValueOption;
import com.xfwang.platform.api.auth.enums.DataClassification;
import com.xfwang.platform.api.auth.enums.ResourceStatus;
import com.xfwang.platform.api.auth.enums.SecurityLevel;
import com.xfwang.platform.api.auth.enums.ConditionOperator;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** 聚合业务模块贡献的授权目录并在启动时校验唯一性。 */
@Service
public class AuthorizationCatalog {

    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{1,63}");
    private static final Pattern MODULE = Pattern.compile("[a-z][a-z0-9-]{1,63}");
    private static final Pattern RESOURCE_TYPE = Pattern.compile("[a-z][a-z0-9-]*(?::[a-z][a-z0-9-]*)+");

    private final Map<String, ResourceTypeDefinition> resourceTypes;
    private final Map<String, String> moduleLabels;
    private final Map<String, ResourceNavigationCategory> moduleCategories;
    private final Map<String, AuthorizationPurposeDefinition> purposes;
    private final List<AuthorizationAttributeDefinition> coreAttributes;

    public AuthorizationCatalog(List<AuthorizationCatalogContributor> contributors) {
        Map<String, ResourceTypeDefinition> typeSnapshot = new LinkedHashMap<>();
        Map<String, String> moduleLabelSnapshot = new LinkedHashMap<>();
        Map<String, ResourceNavigationCategory> moduleCategorySnapshot = new LinkedHashMap<>();
        Map<String, AuthorizationPurposeDefinition> purposeSnapshot = new LinkedHashMap<>();
        Set<String> modules = new java.util.HashSet<>();
        for (AuthorizationCatalogContributor contributor : contributors) {
            String moduleCode = contributor.moduleCode();
            String moduleLabel = contributor.moduleLabel();
            require(MODULE.matcher(nullSafe(moduleCode)).matches(), "授权目录模块编码无效");
            require(!nullSafe(moduleLabel).isBlank(), "授权目录模块名称无效: " + moduleCode);
            require(contributor.navigationCategory() != null, "授权目录模块分类无效: " + moduleCode);
            require(modules.add(moduleCode), "授权目录模块编码重复: " + moduleCode);
            moduleLabelSnapshot.put(moduleCode, moduleLabel);
            moduleCategorySnapshot.put(moduleCode, contributor.navigationCategory());
            for (ResourceTypeDefinition definition : safe(contributor.resourceTypes())) {
                validateType(moduleCode, definition);
                require(typeSnapshot.putIfAbsent(definition.typeCode(), definition) == null,
                    "授权资源类型编码重复: " + definition.typeCode());
            }
            for (AuthorizationPurposeDefinition definition : safe(contributor.purposes())) {
                require(definition != null && CODE.matcher(nullSafe(definition.code())).matches()
                    && !nullSafe(definition.label()).isBlank(), "授权用途定义无效");
                require(purposeSnapshot.putIfAbsent(definition.code(), definition) == null,
                    "授权用途编码重复: " + definition.code());
            }
        }
        purposeSnapshot.values().forEach(definition -> {
            require(!definition.resourceTypeCodes().isEmpty(),
                "授权用途必须声明适用资源类型: " + definition.code());
            definition.resourceTypeCodes().forEach(typeCode -> require(typeSnapshot.containsKey(typeCode),
                "授权用途引用了未注册资源类型: " + definition.code() + " -> " + typeCode));
        });
        resourceTypes = Map.copyOf(typeSnapshot);
        moduleLabels = Map.copyOf(moduleLabelSnapshot);
        moduleCategories = Map.copyOf(moduleCategorySnapshot);
        purposes = Map.copyOf(purposeSnapshot);
        coreAttributes = List.copyOf(coreAttributes());
    }

    public List<ResourceTypeDefinition> resourceTypes() {
        return new ArrayList<>(resourceTypes.values());
    }

    public Optional<ResourceTypeDefinition> findResourceType(String typeCode) {
        return Optional.ofNullable(resourceTypes.get(typeCode));
    }

    public String moduleLabel(String moduleCode) {
        return moduleLabels.getOrDefault(moduleCode, moduleCode);
    }

    public ResourceNavigationCategory navigationCategory(String moduleCode) {
        return Optional.ofNullable(moduleCategories.get(moduleCode))
            .orElseThrow(() -> new ServiceException(GlobalErrorCode.VALIDATION_ERROR,
                "授权目录模块分类不存在: " + moduleCode));
    }

    public List<ResourceNavigationCategory> navigationCategories() {
        return moduleCategories.values().stream().distinct()
            .sorted(java.util.Comparator.comparingInt(ResourceNavigationCategory::order))
            .toList();
    }

    public List<AuthorizationPurposeDefinition> purposes() {
        return new ArrayList<>(purposes.values());
    }

    public Optional<AuthorizationPurposeDefinition> findPurpose(String purposeCode) {
        return Optional.ofNullable(purposes.get(purposeCode));
    }

    public List<AuthorizationPurposeDefinition> purposesFor(String typeCode) {
        return purposes.values().stream()
            .filter(definition -> definition.resourceTypeCodes().contains(typeCode))
            .toList();
    }

    public boolean supportsPurpose(String typeCode, String purposeCode) {
        return findPurpose(purposeCode)
            .map(AuthorizationPurposeDefinition::resourceTypeCodes)
            .orElse(Set.of())
            .contains(typeCode);
    }

    public boolean supportsAction(String typeCode, String actionCode) {
        return findResourceType(typeCode)
            .map(ResourceTypeDefinition::actions)
            .orElse(List.of()).stream()
            .map(AuthorizationActionDefinition::code)
            .anyMatch(actionCode::equals);
    }

    public List<AuthorizationAttributeDefinition> attributesFor(String typeCode) {
        List<AuthorizationAttributeDefinition> result = new ArrayList<>(coreAttributes);
        findResourceType(typeCode).ifPresent(type -> result.addAll(type.attributes()));
        return List.copyOf(result);
    }

    private void validateType(String moduleCode, ResourceTypeDefinition definition) {
        require(definition != null
            && moduleCode.equals(definition.sourceModule())
            && CODE.matcher(nullSafe(definition.domainCode())).matches()
            && !nullSafe(definition.domainLabel()).isBlank()
            && RESOURCE_TYPE.matcher(nullSafe(definition.typeCode())).matches()
            && !nullSafe(definition.typeLabel()).isBlank()
            && !definition.selectionModes().isEmpty()
            && !definition.capabilities().isEmpty(), "授权资源类型定义无效");
        Set<String> actionCodes = new java.util.HashSet<>();
        definition.actions().forEach(action -> require(action != null
            && CODE.matcher(nullSafe(action.code())).matches()
            && !nullSafe(action.label()).isBlank()
            && actionCodes.add(action.code()), "授权动作定义无效或重复: " + definition.typeCode()));
        Set<String> attributePaths = new java.util.HashSet<>();
        definition.attributes().forEach(attribute -> require(attribute != null
            && nullSafe(attribute.path()).startsWith("resource.")
            && !nullSafe(attribute.label()).isBlank()
            && attribute.valueType() != null
            && !attribute.operators().isEmpty()
            && attribute.operandRole() != null
            && attribute.visibility() != null
            && attributePaths.add(attribute.path()), "授权属性定义无效或重复: " + definition.typeCode()));
    }

    private List<AuthorizationAttributeDefinition> coreAttributes() {
        Set<ConditionOperator> equality = Set.of(ConditionOperator.EQ, ConditionOperator.NE,
            ConditionOperator.IN, ConditionOperator.NOT_IN, ConditionOperator.EXISTS);
        Set<ConditionOperator> ordered = Set.of(ConditionOperator.EQ, ConditionOperator.NE,
            ConditionOperator.GT, ConditionOperator.GTE, ConditionOperator.LT, ConditionOperator.LTE,
            ConditionOperator.EXISTS);
        return List.of(
            attribute("subject.userId", "当前用户", AttributeValueType.INTEGER, equality,
                AttributeOperandRole.RIGHT, AttributeVisibility.ADVANCED),
            attribute("subject.roleIds", "角色ID集合", AttributeValueType.INTEGER_SET,
                Set.of(ConditionOperator.CONTAINS, ConditionOperator.EXISTS),
                AttributeOperandRole.RIGHT, AttributeVisibility.ADVANCED),
            attribute("subject.deptId", "当前用户所属部门", AttributeValueType.INTEGER, equality,
                AttributeOperandRole.RIGHT, AttributeVisibility.ADVANCED),
            attribute("subject.deptPathIds", "所属部门祖先链", AttributeValueType.INTEGER_SET,
                Set.of(ConditionOperator.CONTAINS, ConditionOperator.EXISTS),
                AttributeOperandRole.RIGHT, AttributeVisibility.ADVANCED),
            attribute("subject.regionCode", "主体行政区划", AttributeValueType.STRING, equality,
                AttributeOperandRole.RIGHT, AttributeVisibility.ADVANCED),
            attribute("subject.securityClearance", "主体安全等级", AttributeValueType.INTEGER, ordered,
                AttributeOperandRole.BOTH, AttributeVisibility.ADVANCED),
            attribute("resource.ownerDeptId", "资源权属部门", AttributeValueType.INTEGER, equality,
                AttributeOperandRole.LEFT, AttributeVisibility.ADVANCED),
            attribute("resource.ownerUserId", "资源所有人", AttributeValueType.INTEGER, equality,
                AttributeOperandRole.LEFT, AttributeVisibility.ADVANCED),
            attribute("resource.ownerDeptPathIds", "资源权属部门路径", AttributeValueType.INTEGER_SET,
                Set.of(ConditionOperator.CONTAINS, ConditionOperator.EXISTS),
                AttributeOperandRole.LEFT, AttributeVisibility.ADVANCED),
            attribute("resource.regionCode", "资源行政区划", AttributeValueType.STRING, equality,
                AttributeOperandRole.LEFT, AttributeVisibility.ADVANCED),
            attribute("resource.classification", "资源安全分类", AttributeValueType.STRING, equality,
                AttributeOperandRole.LEFT, AttributeVisibility.BASIC,
                java.util.Arrays.stream(DataClassification.values())
                    .map(value -> new AuthorizationValueOption(value.label(), value.code())).toList()),
            attribute("resource.securityLevel", "资源安全等级", AttributeValueType.INTEGER, ordered,
                AttributeOperandRole.BOTH, AttributeVisibility.BASIC,
                java.util.Arrays.stream(SecurityLevel.values())
                    .map(value -> new AuthorizationValueOption(value.label(), String.valueOf(value.level()))).toList()),
            attribute("resource.status", "授权资源状态", AttributeValueType.STRING, equality,
                AttributeOperandRole.LEFT, AttributeVisibility.ADVANCED,
                java.util.Arrays.stream(ResourceStatus.values())
                    .map(value -> new AuthorizationValueOption(value.label(), value.code())).toList()),
            attribute("environment.purpose", "访问用途", AttributeValueType.STRING, equality,
                AttributeOperandRole.LEFT, AttributeVisibility.ADVANCED),
            attribute("environment.networkZone", "可信网络区域", AttributeValueType.STRING, equality,
                AttributeOperandRole.LEFT, AttributeVisibility.ADVANCED,
                List.of(new AuthorizationValueOption("政务内网", "GOV_INTERNAL"),
                    new AuthorizationValueOption("政务外网", "GOV_EXTRANET"))),
            attribute("environment.requestTime", "请求时间", AttributeValueType.DATETIME, ordered,
                AttributeOperandRole.LEFT, AttributeVisibility.ADVANCED));
    }

    private AuthorizationAttributeDefinition attribute(String path, String label,
                                                       AttributeValueType type,
                                                       Set<ConditionOperator> operators) {
        return new AuthorizationAttributeDefinition(path, label, type, operators);
    }

    private AuthorizationAttributeDefinition attribute(String path, String label,
                                                       AttributeValueType type,
                                                       Set<ConditionOperator> operators,
                                                       AttributeOperandRole operandRole,
                                                       AttributeVisibility visibility) {
        return new AuthorizationAttributeDefinition(path, label, type, operators,
            operandRole, visibility, List.of());
    }

    private AuthorizationAttributeDefinition attribute(String path, String label,
                                                       AttributeValueType type,
                                                       Set<ConditionOperator> operators,
                                                       AttributeOperandRole operandRole,
                                                       AttributeVisibility visibility,
                                                       List<AuthorizationValueOption> valueOptions) {
        return new AuthorizationAttributeDefinition(path, label, type, operators,
            operandRole, visibility, valueOptions);
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private void require(boolean valid, String message) {
        if (!valid) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, message);
        }
    }
}
