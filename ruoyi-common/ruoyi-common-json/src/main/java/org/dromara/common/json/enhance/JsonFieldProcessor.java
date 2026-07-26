package org.dromara.common.json.enhance;

/**
 * JSON 字段增强处理器，按收集、批量准备、渲染三个阶段执行。
 */
public interface JsonFieldProcessor {

    default boolean supports(JsonFieldContext fieldContext) {
        return true;
    }

    default void collect(JsonFieldContext fieldContext, JsonEnhancementContext context) {
    }

    default void prepare(JsonEnhancementContext context) {
    }

    default Object process(JsonFieldContext fieldContext, Object value, JsonEnhancementContext context) {
        return value;
    }
}
