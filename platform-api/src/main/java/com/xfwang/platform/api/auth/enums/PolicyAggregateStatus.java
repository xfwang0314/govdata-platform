package com.xfwang.platform.api.auth.enums;

/** 策略聚合生命周期；只描述整条策略是否参与授权决策。 */
public enum PolicyAggregateStatus {

    DRAFT("待发布"),
    ACTIVE("生效中"),
    DISABLED("已停用");

    private final String label;

    PolicyAggregateStatus(String label) {
        this.label = label;
    }

    public String code() {
        return name();
    }

    public String label() {
        return label;
    }
}
