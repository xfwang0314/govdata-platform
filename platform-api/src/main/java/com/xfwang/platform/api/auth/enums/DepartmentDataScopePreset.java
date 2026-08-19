package com.xfwang.platform.api.auth.enums;

import java.util.Arrays;

/** 将传统部门数据范围安全转换为 ABAC 条件的固定预设。 */
public enum DepartmentDataScopePreset {

    ALL("全部数据权限"),
    CUSTOM("自定义数据权限"),
    DEPARTMENT("本部门数据权限"),
    DEPARTMENT_AND_DESCENDANTS("本部门及以下数据权限"),
    SELF("仅本人数据权限"),
    DEPARTMENT_AND_DESCENDANTS_OR_SELF("本部门及以下或本人数据权限");

    private final String label;

    DepartmentDataScopePreset(String label) {
        this.label = label;
    }

    public String code() {
        return name();
    }

    public String label() {
        return label;
    }

    public static DepartmentDataScopePreset parse(String code) {
        return Arrays.stream(values())
            .filter(value -> value.name().equalsIgnoreCase(code == null ? "" : code.trim()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("部门数据范围预设无效"));
    }
}
