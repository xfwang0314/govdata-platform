package org.dromara.common.mybatis.helper;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.model.SaStorage;
import cn.hutool.core.util.ObjectUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.dromara.common.mybatis.annotation.AbacDataScope;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** ABAC 数据范围执行上下文。 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("unchecked")
public class AbacDataScopeHelper {

    private static final String CONTEXT_KEY = "abac:data-scope";
    private static final ThreadLocal<AbacDataScope> SCOPE_CACHE = new ThreadLocal<>();

    public static AbacDataScope getScope() {
        return SCOPE_CACHE.get();
    }

    public static void setScope(AbacDataScope scope) {
        SCOPE_CACHE.set(scope);
    }

    public static void removeScope() {
        SCOPE_CACHE.remove();
    }

    public static <T> T getVariable(String key) {
        return (T) getContext().get(key);
    }

    public static void setVariable(String key, Object value) {
        getContext().put(key, value);
    }

    public static Map<String, Object> getContext() {
        SaStorage storage = SaHolder.getStorage();
        Object attribute = storage.get(CONTEXT_KEY);
        if (ObjectUtil.isNull(attribute)) {
            storage.set(CONTEXT_KEY, new HashMap<>());
            attribute = storage.get(CONTEXT_KEY);
        }
        if (attribute instanceof Map map) {
            return map;
        }
        throw new IllegalStateException("ABAC data scope context type is invalid");
    }

    public static void ignore(Runnable handle) {
        AbacDataScopeIgnoreContext.enable();
        try {
            handle.run();
        } finally {
            AbacDataScopeIgnoreContext.disable();
        }
    }

    public static <T> T ignore(Supplier<T> handle) {
        AbacDataScopeIgnoreContext.enable();
        try {
            return handle.get();
        } finally {
            AbacDataScopeIgnoreContext.disable();
        }
    }
}
