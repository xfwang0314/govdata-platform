package com.xfwang.platform.api.auth.catalog;

/** 授权属性在条件表达式中允许出现的位置。 */
public enum AttributeOperandRole {
    LEFT,
    RIGHT,
    BOTH;

    public boolean supportsLeft() {
        return this == LEFT || this == BOTH;
    }

    public boolean supportsRight() {
        return this == RIGHT || this == BOTH;
    }
}
