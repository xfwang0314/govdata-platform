package com.xfwang.govdata.datasource.infrastructure.connector.http;

/** OAuth2 动态令牌获取 Interface。 */
public interface AccessTokenProvider {
    OAuth2AccessToken acquire(OAuth2TokenRequest request);

    OAuth2AccessToken refresh(OAuth2TokenRequest request);
}
