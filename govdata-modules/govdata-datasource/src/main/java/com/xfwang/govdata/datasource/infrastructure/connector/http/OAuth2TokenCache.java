package com.xfwang.govdata.datasource.infrastructure.connector.http;

/** OAuth2 短期令牌缓存 Interface。 */
public interface OAuth2TokenCache {
    OAuth2AccessToken getOrLoad(String cacheKey, TokenLoader loader);

    void evict(String cacheKey);

    @FunctionalInterface
    interface TokenLoader {
        OAuth2AccessToken load();
    }
}
