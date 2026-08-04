package com.xfwang.platform.auth.decision;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 使用平台统一 ObjectMapper 解析授权 JSON，避免授权核心依赖静态 Spring 上下文。
 */
@Component
@RequiredArgsConstructor
public class AuthorizationJsonCodec {

    private final ObjectMapper objectMapper;

    public <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("授权 JSON 格式无效", ex);
        }
    }

    public <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("授权 JSON 格式无效", ex);
        }
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("授权审计 JSON 序列化失败", ex);
        }
    }
}
