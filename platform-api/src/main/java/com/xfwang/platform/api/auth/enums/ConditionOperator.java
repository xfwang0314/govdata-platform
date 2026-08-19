package com.xfwang.platform.api.auth.enums;

/**
 * ABAC 第一阶段支持的安全操作符。
 *
 * <p>策略只能使用受限操作符，不允许保存任意 Java、SpEL 或 SQL。</p>
 */
public enum ConditionOperator {

    EQ("等于"),

    NE("不等于"),

    IN("属于集合"),

    NOT_IN("不属于集合"),

    CONTAINS("包含"),

    GT("大于"),

    GTE("大于等于"),

    LT("小于"),

    LTE("小于等于"),

    BETWEEN("介于"),

    INTERSECTS("存在交集"),

    STARTS_WITH("开头匹配"),

    EXISTS("存在"),

    NOT_EXISTS("不存在");

    private final String label;

    ConditionOperator(String label) {
        this.label = label;
    }

    public String code() {
        return name();
    }

    public String label() {
        return label;
    }
}
