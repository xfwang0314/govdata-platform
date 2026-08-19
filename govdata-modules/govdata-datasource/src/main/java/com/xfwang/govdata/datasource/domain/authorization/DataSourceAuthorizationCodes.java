package com.xfwang.govdata.datasource.domain.authorization;

/**
 * 数据源领域拥有的稳定授权编码。
 *
 * <p>这里使用编译期常量是为了同时支持 {@code ResourceAuthorize} 注解和授权目录枚举；
 * 它不是跨领域的全局常量集合。</p>
 */
public final class DataSourceAuthorizationCodes {

    public static final String MODULE = "govdata-datasource";
    public static final String RESOURCE_TYPE = "govdata:datasource";

    private DataSourceAuthorizationCodes() {
    }

    /** 数据源资源支持的动作编码。 */
    public static final class Action {
        public static final String VIEW = "VIEW";
        public static final String UPDATE = "UPDATE";
        public static final String TEST_CONNECTION = "TEST_CONNECTION";
        public static final String ENABLE = "ENABLE";
        public static final String DISABLE = "DISABLE";
        public static final String REMOVE = "REMOVE";
        public static final String METADATA_INSPECT = "METADATA_INSPECT";
        public static final String USE_AS_SOURCE = "USE_AS_SOURCE";
        public static final String USE_AS_TARGET = "USE_AS_TARGET";
        public static final String AUTHORIZE = "AUTHORIZE";

        private Action() {
        }
    }

    /** 数据源资源支持的用途编码。 */
    public static final class Purpose {
        public static final String ADMINISTRATION = "DATASOURCE_ADMINISTRATION";
        public static final String METADATA_INSPECTION = "METADATA_INSPECTION";
        public static final String DATA_EXCHANGE_SOURCE = "DATA_EXCHANGE_SOURCE";
        public static final String DATA_EXCHANGE_TARGET = "DATA_EXCHANGE_TARGET";

        private Purpose() {
        }
    }
}
