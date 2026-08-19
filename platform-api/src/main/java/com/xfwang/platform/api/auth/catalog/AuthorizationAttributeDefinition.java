package com.xfwang.platform.api.auth.catalog;

import com.xfwang.platform.api.auth.enums.ConditionOperator;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;
import java.util.List;

/** 策略构建器允许使用的一个受类型约束属性。 */
public record AuthorizationAttributeDefinition(String path, String label,
                                                AttributeValueType valueType,
                                                Set<ConditionOperator> operators,
                                                AttributeOperandRole operandRole,
                                                AttributeVisibility visibility,
                                                List<AuthorizationValueOption> valueOptions)
    implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public AuthorizationAttributeDefinition {
        operators = operators == null ? Set.of() : Set.copyOf(operators);
        operandRole = operandRole == null ? AttributeOperandRole.BOTH : operandRole;
        visibility = visibility == null ? AttributeVisibility.ADVANCED : visibility;
        valueOptions = valueOptions == null ? List.of() : List.copyOf(valueOptions);
    }

    public AuthorizationAttributeDefinition(String path, String label,
                                            AttributeValueType valueType,
                                            Set<ConditionOperator> operators) {
        this(path, label, valueType, operators, AttributeOperandRole.BOTH,
            AttributeVisibility.ADVANCED, List.of());
    }
}
