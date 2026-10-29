package com.xfwang.govdata.query.application;

import com.xfwang.govdata.api.query.AuthorizedQuery;
import com.xfwang.govdata.api.query.QueryAuthorization;
import com.xfwang.govdata.api.query.QueryResult;
import com.xfwang.platform.api.auth.annotation.ResourceAuthorize;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 建立新鲜的表 QUERY 授权上下文，再由执行核心消费 Obligation。 */
@Service
@RequiredArgsConstructor
public class AuthorizedQueryExecutionService {
    private final DefaultAuthorizedQueryGateway executor;

    @ResourceAuthorize(resourceId = "#query.resourceId", resourceType = QueryAuthorization.TABLE_RESOURCE_TYPE,
        action = QueryAuthorization.ACTION, purpose = "#query.purpose")
    public QueryResult execute(AuthorizedQuery query) {
        return executor.execute(query);
    }
}
