package com.xfwang.platform.api.auth.enums;

/**
 * ABAC 第一阶段支持的安全操作符。
 *
 * <p>策略只能使用受限操作符，不允许保存任意 Java、SpEL 或 SQL。</p>
 */
public enum ConditionOperator {

    EQ,

    NE,

    IN,

    NOT_IN,

    CONTAINS,

    GT,

    GTE,

    LT,

    LTE,

    EXISTS
}
