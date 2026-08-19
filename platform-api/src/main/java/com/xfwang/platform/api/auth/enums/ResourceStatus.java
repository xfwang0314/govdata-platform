package com.xfwang.platform.api.auth.enums;

/** 授权资源投影状态。 */
public enum ResourceStatus {
    ACTIVE("生效"),
    DISABLED("停用"),
    ARCHIVED("已归档");

    private final String label;

    ResourceStatus(String label) {
        this.label = label;
    }

    public String code() {
        return name();
    }

    public String label() {
        return label;
    }
}
