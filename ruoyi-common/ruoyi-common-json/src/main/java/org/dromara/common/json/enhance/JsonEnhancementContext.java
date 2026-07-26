package org.dromara.common.json.enhance;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 单次 JSON 响应增强上下文。
 */
@Getter
public class JsonEnhancementContext {

    private final ObjectMapper objectMapper;
    private final Map<String, Object> attributes = new LinkedHashMap<>();
    private boolean processingRequired;

    public JsonEnhancementContext(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getOrCreateAttribute(String key, Supplier<T> supplier) {
        return (T) attributes.computeIfAbsent(key, ignored -> supplier.get());
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    public void markProcessingRequired() {
        processingRequired = true;
    }
}
