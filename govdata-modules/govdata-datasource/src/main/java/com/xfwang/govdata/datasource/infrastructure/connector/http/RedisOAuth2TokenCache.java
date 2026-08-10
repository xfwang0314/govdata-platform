package com.xfwang.govdata.datasource.infrastructure.connector.http;

import org.dromara.common.redis.utils.RedisUtils;
import org.redisson.api.RLock;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** 多实例共享的 OAuth2 短期令牌缓存；Redis 必须启用认证、TLS 与最小访问权限。 */
@Component
public class RedisOAuth2TokenCache implements OAuth2TokenCache {

    private static final String CACHE_PREFIX = "govdata:oauth2:token:";
    private static final String LOCK_PREFIX = "govdata:oauth2:lock:";

    @Override
    public OAuth2AccessToken getOrLoad(String cacheKey, TokenLoader loader) {
        String key = CACHE_PREFIX + cacheKey;
        OAuth2AccessToken cached = RedisUtils.getCacheObject(key);
        if (cached != null) {
            return cached;
        }
        RLock lock = RedisUtils.getClient().getLock(LOCK_PREFIX + cacheKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(5, 30, TimeUnit.SECONDS);
            if (!acquired) {
                throw new IllegalStateException("OAuth2 token refresh lock unavailable");
            }
            cached = RedisUtils.getCacheObject(key);
            if (cached != null) {
                return cached;
            }
            OAuth2AccessToken loaded = loader.load();
            long ttl = Math.max(5, loaded.expiresInSeconds() - Math.min(60, loaded.expiresInSeconds() / 10));
            RedisUtils.setCacheObject(key, loaded, Duration.ofSeconds(ttl));
            return loaded;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OAuth2 token refresh interrupted", exception);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public void evict(String cacheKey) {
        RedisUtils.deleteObject(CACHE_PREFIX + cacheKey);
    }
}
