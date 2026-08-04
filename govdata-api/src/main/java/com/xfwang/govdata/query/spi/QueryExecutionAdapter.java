package com.xfwang.govdata.query.spi;

/** 各 Connector 提供的只读查询执行 Adapter。 */
public interface QueryExecutionAdapter {

    boolean supports(String connectorCode);

    RawQueryResult execute(AuthorizedExecutionPlan plan);
}
