package com.xfwang.platform.auth.application.policy;

import com.xfwang.platform.api.auth.catalog.AuthorizationAttributeDefinition;
import com.xfwang.platform.api.auth.catalog.AttributeValueType;
import com.xfwang.platform.api.auth.catalog.ResourceSelectionMode;
import com.xfwang.platform.api.auth.catalog.ResourceAuthorizationCapability;
import com.xfwang.platform.api.auth.catalog.ResourceTypeDefinition;
import com.xfwang.platform.api.auth.enums.ConditionOperator;
import com.xfwang.platform.api.auth.model.ResourceDescriptor;
import com.xfwang.platform.api.auth.service.ResourceRegistry;
import com.xfwang.platform.auth.application.catalog.AuthorizationCatalog;
import com.xfwang.platform.auth.application.policy.command.PolicyContentCommand;
import com.xfwang.platform.auth.application.policy.command.PolicyGroupDraftCommand;
import com.xfwang.platform.auth.application.policy.command.PolicyRuleCommand;
import com.xfwang.platform.auth.application.policy.command.PolicyResourceCommand;
import com.xfwang.platform.auth.domain.policy.model.PolicyCondition;
import com.xfwang.platform.auth.domain.policy.model.PolicyConditionGroup;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 校验 Policy 只能引用业务模块注册过的资源类型、动作和安全属性。 */
@Component
@RequiredArgsConstructor
public class AuthorizationPolicySchemaValidator {

    private static final int MAX_GROUP_DEPTH = 5;
    private static final int MAX_CONDITION_COUNT = 50;

    private final AuthorizationCatalog catalog;
    private final ResourceRegistry resourceRegistry;
    private final com.xfwang.platform.auth.application.decision.AuthorizationJsonCodec jsonCodec;

    public void validate(PolicyGroupDraftCommand command) {
        if (command == null || StringUtils.isBlank(command.getPolicyName())
            || !"ANY".equals(normalize(command.getSubjectOperator()))) {
            throw invalid("策略名称不能为空，第一版主体表达式只支持 ANY");
        }
        if (command.getSubjects() == null || command.getSubjects().isEmpty()
            || command.getRules() == null || command.getRules().isEmpty()) {
            throw invalid("主体和授权规则不能为空");
        }
        boolean any = command.getSubjects().stream()
            .anyMatch(subject -> "ANY".equals(normalize(subject.getSubjectType())));
        if (any && command.getSubjects().size() != 1) {
            throw invalid("ANY 主体必须独占，不能与其他主体组合");
        }
        command.getSubjects().forEach(subject -> {
            String type = normalize(subject.getSubjectType());
            try {
                com.xfwang.platform.api.auth.enums.SubjectType.valueOf(type);
            } catch (RuntimeException exception) {
                throw invalid("主体类型无效: " + type);
            }
            if (!"ANY".equals(type) && StringUtils.isBlank(subject.getSubjectId())) {
                throw invalid("主体稳定标识不能为空");
            }
            if (Boolean.TRUE.equals(subject.getIncludeChildren()) && !"DEPARTMENT".equals(type)) {
                throw invalid("只有部门主体可以包含下级部门");
            }
        });
        java.util.Set<String> ruleCodes = new java.util.HashSet<>();
        for (PolicyRuleCommand rule : command.getRules()) {
            validateRule(rule, ruleCodes, ResourceAuthorizationCapability.CUSTOM_POLICY);
        }
    }

    private void validateRule(PolicyRuleCommand rule, java.util.Set<String> ruleCodes,
                              ResourceAuthorizationCapability capability) {
        if (rule == null || StringUtils.isBlank(rule.getResourceType())) {
            throw invalid("规则资源类型不能为空");
        }
        if (StringUtils.isNotBlank(rule.getRuleCode()) && !ruleCodes.add(rule.getRuleCode())) {
            throw invalid("规则编码重复: " + rule.getRuleCode());
        }
        String resourceType = rule.getResourceType().trim();
        ResourceTypeDefinition definition = catalog.findResourceType(resourceType)
            .orElseThrow(() -> invalid("资源类型未注册: " + resourceType));
        requirePolicyAuthoringCapability(resourceType, capability);
        String scope = normalize(rule.getResourceScope());
        if (!Set.of("TYPE", "INSTANCE").contains(scope)) {
            throw invalid("资源范围只能是 TYPE 或 INSTANCE");
        }
        requireSelectionMode(resourceType, ResourceSelectionMode.valueOf(scope));
        if ("TYPE".equals(scope) && rule.getTargets() != null && !rule.getTargets().isEmpty()) {
            throw invalid("同类型全部资源规则不能同时指定资源实例");
        }
        if ("INSTANCE".equals(scope) && (rule.getTargets() == null || rule.getTargets().isEmpty())) {
            throw invalid("实例资源规则必须至少选择一个资源");
        }
        if (rule.getTargets() != null) {
            Set<Long> ids = new HashSet<>();
            rule.getTargets().forEach(target -> {
                if (target == null || target.getResourceId() == null || !ids.add(target.getResourceId())) {
                    throw invalid("资源实例不能为空或重复");
                }
                ResourceDescriptor resource = resourceRegistry.findById(target.getResourceId())
                    .orElseThrow(() -> new ServiceException(GlobalErrorCode.NOT_FOUND, "策略绑定的授权资源不存在"));
                if (!resourceType.equals(resource.getResourceType())) {
                    throw invalid("资源实例与规则资源类型不一致");
                }
                if (Boolean.TRUE.equals(target.getIncludeChildren()) && definition.parentTypeCodes().isEmpty()
                    && catalog.resourceTypes().stream().noneMatch(type -> type.parentTypeCodes().contains(resourceType))) {
                    throw invalid("资源类型 " + definition.typeLabel() + " 不支持包含子资源");
                }
            });
        }
        if (rule.getActions() == null || rule.getActions().isEmpty()
            || rule.getPurposes() == null || rule.getPurposes().isEmpty()) {
            throw invalid("规则动作和访问目的不能为空");
        }
        rule.getActions().forEach(action -> {
            if (!catalog.supportsAction(resourceType, normalize(action))) {
                throw invalid("动作 " + action + " 不属于资源类型 " + resourceType);
            }
        });
        rule.getPurposes().forEach(purpose -> {
            String normalizedPurpose = normalize(purpose);
            if (!catalog.supportsPurpose(resourceType, normalizedPurpose)) {
                throw invalid("访问目的 " + purpose + " 不适用于资源类型 " + resourceType);
            }
        });
        PolicyConditionGroup condition = jsonCodec.read(
            StringUtils.isBlank(rule.getConditionJson())
                ? "{\"logic\":\"ALL\",\"conditions\":[],\"groups\":[]}"
                : rule.getConditionJson(), PolicyConditionGroup.class);
        Counter counter = new Counter();
        validateGroup(condition, catalog.attributesFor(resourceType).stream()
            .collect(Collectors.toMap(AuthorizationAttributeDefinition::path, Function.identity())), 1, counter);
        if (StringUtils.isNotBlank(rule.getObligationJson())) {
            jsonCodec.read(rule.getObligationJson(), com.xfwang.platform.api.auth.model.AuthorizationObligation.class);
        }
    }

    public void validate(PolicyContentCommand command, PolicyConditionGroup conditionGroup) {
        validate(command, conditionGroup, ResourceAuthorizationCapability.CUSTOM_POLICY);
    }

    public void validate(PolicyContentCommand command, PolicyConditionGroup conditionGroup,
                         ResourceAuthorizationCapability requiredCapability) {
        Set<String> resourceTypes = resolveResourceTypes(command.getResources(), requiredCapability);
        for (String action : command.getActions()) {
            String normalizedAction = normalize(action);
            for (String resourceType : resourceTypes) {
                if (!catalog.supportsAction(resourceType, normalizedAction)) {
                    throw invalid("动作 " + normalizedAction + " 不属于资源类型 " + resourceType);
                }
            }
        }
        Map<String, AuthorizationAttributeDefinition> attributes = commonAttributes(resourceTypes);
        Counter counter = new Counter();
        validateGroup(conditionGroup, attributes, 1, counter);
    }

    private Set<String> resolveResourceTypes(Iterable<PolicyResourceCommand> resources,
                                             ResourceAuthorizationCapability requiredCapability) {
        Set<String> result = new HashSet<>();
        for (PolicyResourceCommand binding : resources) {
            if (binding == null || !binding.isSelectorValid()) {
                throw invalid("resourceId 与 resourceType 必须且只能填写一个");
            }
            if (binding.getResourceId() != null) {
                ResourceDescriptor resource = resourceRegistry.findById(binding.getResourceId())
                    .orElseThrow(() -> new ServiceException(GlobalErrorCode.NOT_FOUND,
                        "策略绑定的授权资源不存在"));
                requireSelectionMode(resource.getResourceType(), ResourceSelectionMode.INSTANCE);
                requirePolicyAuthoringCapability(resource.getResourceType(), requiredCapability);
                result.add(resource.getResourceType());
            } else {
                String resourceType = binding.getResourceType().trim();
                catalog.findResourceType(resourceType).orElseThrow(() -> invalid("资源类型未注册: " + resourceType));
                requireSelectionMode(resourceType, ResourceSelectionMode.TYPE);
                requirePolicyAuthoringCapability(resourceType, requiredCapability);
                result.add(resourceType);
            }
        }
        return result;
    }

    private void requirePolicyAuthoringCapability(String resourceType,
                                                  ResourceAuthorizationCapability requiredCapability) {
        ResourceTypeDefinition definition = catalog.findResourceType(resourceType)
            .orElseThrow(() -> invalid("资源类型未注册: " + resourceType));
        if (requiredCapability == null || !definition.capabilities().contains(requiredCapability)) {
            throw invalid("资源类型 " + definition.typeLabel() + " 不支持当前策略创建方式");
        }
    }

    private void requireSelectionMode(String resourceType, ResourceSelectionMode mode) {
        ResourceTypeDefinition definition = catalog.findResourceType(resourceType)
            .orElseThrow(() -> invalid("资源类型未注册: " + resourceType));
        if (!definition.selectionModes().contains(mode)) {
            throw invalid("资源类型 " + definition.typeLabel() + " 不支持 " + mode + " 授权粒度");
        }
    }

    private Map<String, AuthorizationAttributeDefinition> commonAttributes(Set<String> resourceTypes) {
        Map<String, AuthorizationAttributeDefinition> common = null;
        for (String resourceType : resourceTypes) {
            Map<String, AuthorizationAttributeDefinition> current = catalog.attributesFor(resourceType).stream()
                .collect(Collectors.toMap(AuthorizationAttributeDefinition::path, Function.identity()));
            if (common == null) {
                common = current;
            } else {
                common.keySet().retainAll(current.keySet());
            }
        }
        return common == null ? Map.of() : common;
    }

    private void validateGroup(PolicyConditionGroup group,
                               Map<String, AuthorizationAttributeDefinition> attributes,
                               int depth, Counter counter) {
        if (group == null || depth > MAX_GROUP_DEPTH
            || !Set.of("ALL", "ANY").contains(normalize(group.getLogic()))) {
            throw invalid("策略条件组结构无效或嵌套超过 " + MAX_GROUP_DEPTH + " 层");
        }
        if (group.getConditions() != null) {
            for (PolicyCondition condition : group.getConditions()) {
                counter.count++;
                if (counter.count > MAX_CONDITION_COUNT) {
                    throw invalid("单个策略最多允许 " + MAX_CONDITION_COUNT + " 个条件");
                }
                validateCondition(condition, attributes);
            }
        }
        if (group.getGroups() != null) {
            group.getGroups().forEach(child -> validateGroup(child, attributes, depth + 1, counter));
        }
    }

    private void validateCondition(PolicyCondition condition,
                                   Map<String, AuthorizationAttributeDefinition> attributes) {
        if (condition == null || condition.getOperator() == null || StringUtils.isBlank(condition.getLeft())) {
            throw invalid("策略条件缺少属性或操作符");
        }
        AuthorizationAttributeDefinition left = attributes.get(condition.getLeft());
        if (left == null || !left.operandRole().supportsLeft()
            || !left.operators().contains(condition.getOperator())) {
            throw invalid("属性或操作符未在授权目录注册: " + condition.getLeft());
        }
        boolean hasRightAttribute = StringUtils.isNotBlank(condition.getRightAttribute());
        boolean hasValue = condition.getValue() != null;
        if (condition.getOperator() == ConditionOperator.EXISTS
            || condition.getOperator() == ConditionOperator.NOT_EXISTS) {
            if (hasRightAttribute || hasValue) {
                throw invalid("EXISTS 操作符不能填写右值");
            }
            return;
        }
        if (hasRightAttribute == hasValue) {
            throw invalid("策略条件 rightAttribute 与 value 必须且只能填写一个");
        }
        if (hasRightAttribute) {
            AuthorizationAttributeDefinition right = attributes.get(condition.getRightAttribute());
            if (right == null || !right.operandRole().supportsRight()) {
                throw invalid("右值属性未在授权目录注册: " + condition.getRightAttribute());
            }
            if (!supportsAttributeComparison(condition.getOperator(), left.valueType(), right.valueType())) {
                throw invalid("操作符 " + condition.getOperator().code() + " 不支持属性类型 "
                    + left.valueType() + " 与 " + right.valueType() + " 比较");
            }
        } else {
            validateConstantValue(condition.getValue(), left, condition.getOperator());
        }
    }

    private void validateConstantValue(Object value, AuthorizationAttributeDefinition attribute,
                                       ConditionOperator operator) {
        if (!attribute.valueOptions().isEmpty()) {
            Set<String> allowed = attribute.valueOptions().stream()
                .map(com.xfwang.platform.api.auth.catalog.AuthorizationValueOption::value)
                .collect(Collectors.toSet());
            if (value instanceof Iterable<?> values) {
                for (Object item : values) {
                    if (!allowed.contains(String.valueOf(item))) {
                        throw invalid("属性 " + attribute.label() + " 的固定值不在允许范围内");
                    }
                }
            } else if (!allowed.contains(String.valueOf(value))) {
                throw invalid("属性 " + attribute.label() + " 的固定值不在允许范围内");
            }
        }
        AttributeValueType expectedType = expectedConstantType(attribute.valueType(), operator);
        boolean valid = switch (expectedType) {
            case INTEGER -> value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long;
            case STRING -> value instanceof String;
            case DATETIME -> value instanceof String string && isDateTime(string);
            case INTEGER_SET -> isIntegerCollection(value);
            case STRING_SET -> isStringCollection(value);
            case BOOLEAN -> value instanceof Boolean;
        };
        if (!valid) {
            throw invalid("属性 " + attribute.label() + " 的固定值类型必须为 " + expectedType);
        }
    }

    private AttributeValueType expectedConstantType(AttributeValueType leftType, ConditionOperator operator) {
        if (operator == ConditionOperator.IN || operator == ConditionOperator.NOT_IN
            || operator == ConditionOperator.INTERSECTS || operator == ConditionOperator.BETWEEN) {
            AttributeValueType collectionType = collectionTypeOf(leftType);
            return collectionType == null ? leftType : collectionType;
        }
        if (operator == ConditionOperator.CONTAINS) {
            AttributeValueType elementType = elementTypeOf(leftType);
            return elementType == null ? leftType : elementType;
        }
        return leftType;
    }

    private boolean isDateTime(String value) {
        try {
            java.time.LocalDateTime.parse(value);
            return true;
        } catch (java.time.format.DateTimeParseException exception) {
            return false;
        }
    }

    private boolean isIntegerCollection(Object value) {
        if (!(value instanceof Iterable<?> values)) {
            return false;
        }
        for (Object item : values) {
            if (!(item instanceof Byte || item instanceof Short || item instanceof Integer || item instanceof Long)) {
                return false;
            }
        }
        return true;
    }

    private boolean isStringCollection(Object value) {
        if (!(value instanceof Iterable<?> values)) {
            return false;
        }
        for (Object item : values) {
            if (!(item instanceof String)) {
                return false;
            }
        }
        return true;
    }

    private boolean supportsAttributeComparison(ConditionOperator operator,
                                                AttributeValueType left,
                                                AttributeValueType right) {
        return switch (operator) {
            case EQ, NE -> left == right;
            case GT, GTE, LT, LTE, BETWEEN -> left == right && isOrderedScalar(left);
            case IN, NOT_IN -> collectionTypeOf(left) != null && collectionTypeOf(left) == right;
            case CONTAINS -> elementTypeOf(left) != null && elementTypeOf(left) == right;
            case INTERSECTS -> left == right
                && (left == AttributeValueType.INTEGER_SET || left == AttributeValueType.STRING_SET);
            case STARTS_WITH -> left == AttributeValueType.STRING && right == AttributeValueType.STRING;
            case EXISTS, NOT_EXISTS -> false;
        };
    }

    private boolean isOrderedScalar(AttributeValueType type) {
        return type == AttributeValueType.DATETIME
            || type == AttributeValueType.INTEGER
            || type == AttributeValueType.STRING;
    }

    private AttributeValueType collectionTypeOf(AttributeValueType type) {
        return switch (type) {
            case INTEGER -> AttributeValueType.INTEGER_SET;
            case STRING -> AttributeValueType.STRING_SET;
            default -> null;
        };
    }

    private AttributeValueType elementTypeOf(AttributeValueType type) {
        return switch (type) {
            case INTEGER_SET -> AttributeValueType.INTEGER;
            case STRING_SET -> AttributeValueType.STRING;
            default -> null;
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private ServiceException invalid(String message) {
        return new ServiceException(GlobalErrorCode.VALIDATION_ERROR, message);
    }

    private static final class Counter {
        private int count;
    }
}
