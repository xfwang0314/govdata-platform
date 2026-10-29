package com.xfwang.govdata.metadata.domain.authorization;

import com.xfwang.govdata.api.metadata.MetadataObjectType;

/** 元数据目录领域拥有的稳定授权编码。 */
public final class MetadataAuthorizationCodes {
    public static final String MODULE = "govdata-metadata";
    public static final String CATALOG_RESOURCE_TYPE = "govdata:metadata:catalog";
    public static final String SCHEMA_RESOURCE_TYPE = "govdata:metadata:schema";
    public static final String TABLE_RESOURCE_TYPE = com.xfwang.govdata.api.query.QueryAuthorization.TABLE_RESOURCE_TYPE;

    private MetadataAuthorizationCodes() { }

    /** 返回能够独立授权的元数据对象对应 Resource Type。 */
    public static String resourceType(MetadataObjectType type) {
        return switch (type) {
            case CATALOG -> CATALOG_RESOURCE_TYPE;
            case SCHEMA -> SCHEMA_RESOURCE_TYPE;
            case TABLE -> TABLE_RESOURCE_TYPE;
            default -> throw new IllegalArgumentException("元数据对象不支持独立授权: " + type);
        };
    }

    public static final class Action {
        public static final String VIEW = "VIEW";
        public static final String QUERY = com.xfwang.govdata.api.query.QueryAuthorization.ACTION;
        public static final String GOVERN = "GOVERN";
        public static final String AUTHORIZE = "AUTHORIZE";
        private Action() { }
    }

    public static final class Purpose {
        public static final String CATALOG = "METADATA_CATALOG";
        public static final String DATA_QUERY = com.xfwang.govdata.api.query.QueryAuthorization.PURPOSE;
        private Purpose() { }
    }
}
