package org.dromara.common.json.enhance;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;

import java.lang.reflect.Array;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一 JSON 响应增强链。
 */
public class JsonValueEnhancer {

    private final ObjectMapper objectMapper;
    private final List<JsonFieldProcessor> processors;
    private final Map<Class<?>, List<PropertyMetadata>> propertyCache = new ConcurrentHashMap<>();

    public JsonValueEnhancer(ObjectMapper objectMapper, List<JsonFieldProcessor> processors) {
        this.objectMapper = objectMapper;
        List<JsonFieldProcessor> sorted = new ArrayList<>(processors);
        AnnotationAwareOrderComparator.sort(sorted);
        this.processors = Collections.unmodifiableList(sorted);
    }

    public Object enhance(Object body) {
        if (body == null || body instanceof JsonNode || processors.isEmpty()) {
            return body;
        }
        JsonEnhancementContext context = new JsonEnhancementContext(objectMapper);
        collectValue(body, context, new IdentityHashMap<>());
        if (!context.isProcessingRequired()) {
            return body;
        }
        processors.forEach(processor -> processor.prepare(context));
        return renderValue(body, context, new IdentityHashMap<>());
    }

    public boolean supports(Class<?> converterType) {
        return !processors.isEmpty()
            && !ByteArrayHttpMessageConverter.class.isAssignableFrom(converterType)
            && !StringHttpMessageConverter.class.isAssignableFrom(converterType)
            && !ResourceHttpMessageConverter.class.isAssignableFrom(converterType);
    }

    private void collectValue(Object value, JsonEnhancementContext context,
                              IdentityHashMap<Object, Boolean> visited) {
        if (value == null) {
            return;
        }
        if (value instanceof Map<?, ?> map) {
            map.values().forEach(child -> collectValue(child, context, visited));
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(child -> collectValue(child, context, visited));
            return;
        }
        if (value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++) {
                collectValue(Array.get(value, i), context, visited);
            }
            return;
        }
        if (isSimpleValue(value.getClass()) || visited.put(value, Boolean.TRUE) != null) {
            return;
        }
        try {
            for (PropertyMetadata metadata : getProperties(value.getClass())) {
                Object propertyValue = metadata.getValue(value);
                JsonFieldContext field = new JsonFieldContext(
                    value, metadata.propertyName(), metadata.member(), propertyValue);
                for (JsonFieldProcessor processor : processors) {
                    if (processor.supports(field)) {
                        context.markProcessingRequired();
                        processor.collect(field, context);
                    }
                }
                collectValue(propertyValue, context, visited);
            }
        } finally {
            visited.remove(value);
        }
    }

    private JsonNode renderValue(Object value, JsonEnhancementContext context,
                                 IdentityHashMap<Object, Boolean> visited) {
        if (value == null) {
            return objectMapper.getNodeFactory().nullNode();
        }
        if (value instanceof JsonNode jsonNode) {
            return jsonNode;
        }
        if (value instanceof Map<?, ?> map) {
            ObjectNode node = objectMapper.createObjectNode();
            map.forEach((key, child) -> node.set(String.valueOf(key), renderValue(child, context, visited)));
            return node;
        }
        if (value instanceof Iterable<?> iterable) {
            ArrayNode node = objectMapper.createArrayNode();
            iterable.forEach(child -> node.add(renderValue(child, context, visited)));
            return node;
        }
        if (value.getClass().isArray()) {
            ArrayNode node = objectMapper.createArrayNode();
            for (int i = 0; i < Array.getLength(value); i++) {
                node.add(renderValue(Array.get(value, i), context, visited));
            }
            return node;
        }
        if (isSimpleValue(value.getClass()) || visited.put(value, Boolean.TRUE) != null) {
            return objectMapper.valueToTree(value);
        }
        try {
            ObjectNode node = objectMapper.createObjectNode();
            for (PropertyMetadata metadata : getProperties(value.getClass())) {
                Object original = metadata.getValue(value);
                Object processed = original;
                JsonFieldContext field = new JsonFieldContext(
                    value, metadata.propertyName(), metadata.member(), original);
                for (JsonFieldProcessor processor : processors) {
                    if (processor.supports(field)) {
                        processed = processor.process(field, processed, context);
                    }
                }
                node.set(metadata.propertyName(), renderValue(processed, context, visited));
            }
            return node;
        } finally {
            visited.remove(value);
        }
    }

    private List<PropertyMetadata> getProperties(Class<?> type) {
        return propertyCache.computeIfAbsent(type, this::resolveProperties);
    }

    private List<PropertyMetadata> resolveProperties(Class<?> type) {
        if (isSimpleValue(type) || type.isArray()
            || Map.class.isAssignableFrom(type) || Iterable.class.isAssignableFrom(type)) {
            return Collections.emptyList();
        }
        JavaType javaType = objectMapper.constructType(type);
        List<BeanPropertyDefinition> definitions =
            objectMapper.getSerializationConfig().introspect(javaType).findProperties();
        List<PropertyMetadata> properties = new ArrayList<>(definitions.size());
        for (BeanPropertyDefinition definition : definitions) {
            AnnotatedMember member = definition.getAccessor();
            if (member == null) {
                member = definition.getField();
            }
            if (member != null) {
                member.fixAccess(true);
                properties.add(new PropertyMetadata(definition.getName(), member));
            }
        }
        return Collections.unmodifiableList(properties);
    }

    private boolean isSimpleValue(Class<?> type) {
        return type.isPrimitive()
            || CharSequence.class.isAssignableFrom(type)
            || Number.class.isAssignableFrom(type)
            || Boolean.class == type
            || Character.class == type
            || java.util.Date.class.isAssignableFrom(type)
            || Temporal.class.isAssignableFrom(type)
            || Enum.class.isAssignableFrom(type)
            || UUID.class.isAssignableFrom(type)
            || Class.class == type;
    }

    private record PropertyMetadata(String propertyName, AnnotatedMember member) {
        Object getValue(Object source) {
            return member.getValue(source);
        }
    }
}
