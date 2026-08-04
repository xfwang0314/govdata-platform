package com.xfwang.platform.auth.policy.model;

import com.xfwang.platform.auth.api.enums.ConditionOperator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 受限 ABAC 条件。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PolicyCondition {

    /**
     * 左值属性路径，例如 subject.securityClearance。
     */
    private String left;

    private ConditionOperator operator;

    /**
     * 右值属性路径；与 value 二选一。
     */
    private String rightAttribute;

    /**
     * 常量右值。
     */
    private Object value;
}
