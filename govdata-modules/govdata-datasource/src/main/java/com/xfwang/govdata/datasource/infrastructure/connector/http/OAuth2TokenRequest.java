package com.xfwang.govdata.datasource.infrastructure.connector.http;

import com.xfwang.govdata.datasource.infrastructure.connector.model.ConnectionTestRequest;

/** OAuth2 Client Credentials 动态令牌请求。 */
public record OAuth2TokenRequest(ConnectionTestRequest connectionContext, String tokenEndpoint,
                                 String scope, String audience, int timeoutSeconds) {
}
