package com.xfwang.govdata.api.query;

/** 所有外部数据在线读取的唯一调用接口。 */
public interface AuthorizedQueryGateway {

    QueryResult execute(AuthorizedQuery query);
}
