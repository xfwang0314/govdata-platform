package org.dromara.common.translation.core.handler;

import cn.hutool.core.util.ObjectUtil;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.core.utils.reflect.ReflectUtils;
import org.dromara.common.json.enhance.JsonEnhancementContext;
import org.dromara.common.json.enhance.JsonFieldContext;
import org.dromara.common.json.enhance.JsonFieldProcessor;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.annotation.TranslationType;
import org.dromara.common.translation.core.TranslationInterface;
import org.springframework.core.annotation.Order;

import java.util.*;

@Slf4j
@Order(0)
public class TranslationJsonFieldProcessor implements JsonFieldProcessor {

    private static final String ATTR_BATCHES = TranslationJsonFieldProcessor.class.getName() + ".batches";
    private static final String ATTR_RESULTS = TranslationJsonFieldProcessor.class.getName() + ".results";
    private final Map<String, TranslationInterface<?>> translationMap;

    public TranslationJsonFieldProcessor(List<TranslationInterface<?>> translations) {
        Map<String, TranslationInterface<?>> map = new LinkedHashMap<>(translations.size());
        for (TranslationInterface<?> translation : translations) {
            TranslationType annotation = translation.getClass().getAnnotation(TranslationType.class);
            if (annotation != null) {
                map.put(annotation.type(), translation);
            }
        }
        this.translationMap = Collections.unmodifiableMap(map);
    }

    @Override
    public boolean supports(JsonFieldContext fieldContext) {
        return fieldContext.getAnnotation(Translation.class) != null;
    }

    @Override
    public void collect(JsonFieldContext fieldContext, JsonEnhancementContext context) {
        Translation annotation = fieldContext.getAnnotation(Translation.class);
        Object sourceValue = resolveSourceValue(fieldContext, annotation);
        if (sourceValue != null) {
            getOrCreateBatches(context)
                .computeIfAbsent(new TranslationBatchKey(annotation.type(), annotation.other()),
                    key -> new LinkedHashSet<>())
                .add(sourceValue);
        }
    }

    @Override
    public void prepare(JsonEnhancementContext context) {
        Map<TranslationBatchKey, Set<Object>> batches = context.getAttribute(ATTR_BATCHES);
        if (batches == null || batches.isEmpty()) {
            return;
        }
        Map<TranslationBatchKey, Map<Object, Object>> results = new LinkedHashMap<>(batches.size());
        for (Map.Entry<TranslationBatchKey, Set<Object>> entry : batches.entrySet()) {
            TranslationInterface<?> translation = translationMap.get(entry.getKey().type());
            if (translation == null) {
                continue;
            }
            try {
                results.put(entry.getKey(), new LinkedHashMap<>(translation.translationBatch(
                    entry.getValue(), entry.getKey().other())));
            } catch (Exception e) {
                log.error("批量翻译处理异常，type: {}, other: {}",
                    entry.getKey().type(), entry.getKey().other(), e);
            }
        }
        context.setAttribute(ATTR_RESULTS, results);
    }

    @Override
    public Object process(JsonFieldContext fieldContext, Object value, JsonEnhancementContext context) {
        Translation annotation = fieldContext.getAnnotation(Translation.class);
        Object sourceValue = resolveSourceValue(fieldContext, annotation);
        if (sourceValue == null) {
            return value;
        }
        TranslationBatchKey batchKey = new TranslationBatchKey(annotation.type(), annotation.other());
        Map<TranslationBatchKey, Map<Object, Object>> results = context.getAttribute(ATTR_RESULTS);
        if (results != null) {
            Map<Object, Object> translated = results.get(batchKey);
            if (translated != null && translated.containsKey(sourceValue)) {
                return translated.get(sourceValue);
            }
        }
        TranslationInterface<?> translation = translationMap.get(annotation.type());
        if (ObjectUtil.isNull(translation)) {
            return value;
        }
        try {
            return translation.translation(sourceValue, annotation.other());
        } catch (Exception e) {
            log.error("翻译处理异常，type: {}, value: {}", annotation.type(), sourceValue, e);
            return value;
        }
    }

    private Map<TranslationBatchKey, Set<Object>> getOrCreateBatches(JsonEnhancementContext context) {
        return context.getOrCreateAttribute(ATTR_BATCHES, LinkedHashMap::new);
    }

    private Object resolveSourceValue(JsonFieldContext fieldContext, Translation translation) {
        if (StringUtils.isNotBlank(translation.mapper())) {
            return ReflectUtils.invokeGetter(fieldContext.owner(), translation.mapper());
        }
        return fieldContext.value();
    }

    private record TranslationBatchKey(String type, String other) {
    }
}
