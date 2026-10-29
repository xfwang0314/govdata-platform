package com.xfwang.govdata.api.query;

/**
 * 元数据表在线查询所共享的稳定授权契约。
 */
public final class QueryAuthorization {
    public static final String TABLE_RESOURCE_TYPE = "govdata:metadata:table";
    public static final String ACTION = "QUERY";
    public static final String PURPOSE = "DATA_QUERY";

    private QueryAuthorization() {
    }
}
