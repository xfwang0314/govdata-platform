package org.dromara.common.translation.core;

import cn.hutool.core.convert.Convert;
import org.dromara.common.core.utils.StreamUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.translation.annotation.TranslationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;

/**
 * 翻译接口 (实现类需标注 {@link TranslationType} 注解标明翻译类型)
 *
 * @author Lion Li
 */
public interface TranslationInterface<T> {

    Logger log = LoggerFactory.getLogger(TranslationInterface.class);

    /**
     * 翻译
     *
     * @param key   需要被翻译的键(不为空)
     * @param other 其他参数
     * @return 返回键对应的值
     */
    T translation(Object key, String other);

    /**
     * 批量翻译。未实现批量能力时兼容回退为逐条翻译。
     */
    default Map<Object, T> translationBatch(Set<Object> keys, String other) {
        TranslationType annotation = this.getClass().getAnnotation(TranslationType.class);
        String type = annotation != null ? annotation.type() : this.getClass().getSimpleName();
        log.warn("翻译类型 [{}] 未覆盖 translationBatch 方法，已退化为逐条查询", type);
        Map<Object, T> result = new LinkedHashMap<>(keys.size());
        for (Object key : keys) {
            result.put(key, translation(key, other));
        }
        return result;
    }

    default Set<Long> collectLongIds(Collection<Object> keys) {
        Set<Long> result = new LinkedHashSet<>();
        for (Object key : keys) {
            if (key instanceof String ids) {
                result.addAll(parseLongIds(ids));
            } else if (key != null) {
                result.add(Convert.toLong(key));
            }
        }
        return result;
    }

    default List<Long> parseLongIds(String ids) {
        return StreamUtils.toList(
            StreamUtils.filter(Arrays.asList(ids.split(StringUtils.SEPARATOR)), StringUtils::isNotBlank),
            value -> Convert.toLong(value.trim())
        );
    }

    default <E> String joinMappedValues(String ids, Function<Long, E> mapper) {
        return StreamUtils.join(parseLongIds(ids), id -> {
            E value = mapper.apply(id);
            return value == null ? null : String.valueOf(value);
        });
    }
}
