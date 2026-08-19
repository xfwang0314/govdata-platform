package com.xfwang.govdata.datasource.domain.enums;

import com.xfwang.govdata.datasource.domain.authorization.DataSourceAuthorizationCodes;

/** 数据源 Resource 支持的稳定授权动作。 */
public enum DataSourceResourceAction {
    VIEW(DataSourceAuthorizationCodes.Action.VIEW, "查看", "LOW"),
    UPDATE(DataSourceAuthorizationCodes.Action.UPDATE, "修改配置", "MEDIUM"),
    TEST_CONNECTION(DataSourceAuthorizationCodes.Action.TEST_CONNECTION, "连接测试", "MEDIUM"),
    ENABLE(DataSourceAuthorizationCodes.Action.ENABLE, "启用", "HIGH"),
    DISABLE(DataSourceAuthorizationCodes.Action.DISABLE, "停用", "HIGH"),
    REMOVE(DataSourceAuthorizationCodes.Action.REMOVE, "删除", "HIGH"),
    METADATA_INSPECT(DataSourceAuthorizationCodes.Action.METADATA_INSPECT, "元数据探测", "MEDIUM"),
    USE_AS_SOURCE(DataSourceAuthorizationCodes.Action.USE_AS_SOURCE, "作为数据来源", "HIGH"),
    USE_AS_TARGET(DataSourceAuthorizationCodes.Action.USE_AS_TARGET, "作为数据目标", "CRITICAL"),
    AUTHORIZE(DataSourceAuthorizationCodes.Action.AUTHORIZE, "转授权限", "CRITICAL");

    private final String code;
    private final String label;
    private final String riskLevel;

    DataSourceResourceAction(String code, String label, String riskLevel) {
        this.code = code;
        this.label = label;
        this.riskLevel = riskLevel;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public String riskLevel() {
        return riskLevel;
    }
}
