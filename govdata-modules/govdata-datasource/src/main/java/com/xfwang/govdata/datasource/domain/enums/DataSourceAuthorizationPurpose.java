package com.xfwang.govdata.datasource.domain.enums;

import com.xfwang.govdata.datasource.domain.authorization.DataSourceAuthorizationCodes;

/** 数据源模块拥有的稳定授权用途。 */
public enum DataSourceAuthorizationPurpose {
    DATASOURCE_ADMINISTRATION(DataSourceAuthorizationCodes.Purpose.ADMINISTRATION, "数据源管理"),
    METADATA_INSPECTION(DataSourceAuthorizationCodes.Purpose.METADATA_INSPECTION, "元数据探测"),
    DATA_EXCHANGE_SOURCE(DataSourceAuthorizationCodes.Purpose.DATA_EXCHANGE_SOURCE, "数据交换来源"),
    DATA_EXCHANGE_TARGET(DataSourceAuthorizationCodes.Purpose.DATA_EXCHANGE_TARGET, "数据交换目标");

    private final String code;
    private final String label;

    DataSourceAuthorizationPurpose(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }
}
