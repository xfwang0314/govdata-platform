package com.xfwang.govdata.datasource.domain.enums;

/** 数据源领域拥有的凭据用途编码。 */
public enum DataSourceSecretUsage {
    CONNECTION_TEST("DATASOURCE_CONNECTION_TEST", "数据源连接测试"),
    METADATA_INSPECT("DATASOURCE_METADATA_INSPECT", "数据源元数据探测"),
    QUERY("DATASOURCE_QUERY", "数据源授权查询");

    public static final String OWNER_MODULE = "govdata-datasource";
    public static final String OWNER_MODULE_LABEL = "数据源管理";

    private final String code;
    private final String label;

    DataSourceSecretUsage(String code, String label) {
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
