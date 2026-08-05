package com.xfwang.platform.auth.domain.policy.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 可递归组合的条件组。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PolicyConditionGroup {

    /**
     * ALL 或 ANY。
     */
    private String logic = "ALL";

    private List<PolicyCondition> conditions = new ArrayList<>();

    private List<PolicyConditionGroup> groups = new ArrayList<>();
}
