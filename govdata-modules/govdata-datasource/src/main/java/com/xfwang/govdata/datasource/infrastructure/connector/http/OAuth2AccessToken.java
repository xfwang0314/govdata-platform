package com.xfwang.govdata.datasource.infrastructure.connector.http;

import java.io.Serializable;

/** 短期 OAuth2 Access Token，仅允许存在于调用内存和受 TTL 约束的缓存。 */
public record OAuth2AccessToken(String value, String tokenType, long expiresInSeconds) implements Serializable {
}
