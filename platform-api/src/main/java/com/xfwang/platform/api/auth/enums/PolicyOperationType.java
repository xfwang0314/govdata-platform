package com.xfwang.platform.api.auth.enums;

/** 策略生命周期操作日志类型。 */
public enum PolicyOperationType {

    CREATE_DRAFT("创建待发布版本"),
    UPDATE_DRAFT("保存待发布版本"),
    CREATE_VERSION("创建待发布版本"),
    PUBLISH("发布版本"),
    DISABLE("停用策略"),
    ENABLE("恢复启用"),
    DISCARD_DRAFT("放弃修改"),
    DELETE("删除策略"),
    RESTORE("从删除区恢复"),
    ROLLBACK_DRAFT("从历史版本创建待发布版本");

    private final String label;

    PolicyOperationType(String label) {
        this.label = label;
    }

    public String code() {
        return name();
    }

    public String label() {
        return label;
    }
}
