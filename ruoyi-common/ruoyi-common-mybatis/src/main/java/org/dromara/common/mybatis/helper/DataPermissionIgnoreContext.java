package org.dromara.common.mybatis.helper;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.dromara.common.core.utils.reflect.ReflectUtils;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 数据权限忽略状态适配器，支持嵌套调用并恢复进入前状态。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class DataPermissionIgnoreContext {

    private static final ThreadLocal<Deque<Boolean>> DATA_PERMISSION_STACK =
        ThreadLocal.withInitial(ArrayDeque::new);

    static void enable() {
        IgnoreStrategy strategy = getIgnoreStrategy();
        DATA_PERMISSION_STACK.get().push(
            strategy != null && Boolean.TRUE.equals(strategy.getDataPermission()));
        if (ObjectUtil.isNull(strategy)) {
            InterceptorIgnoreHelper.handle(IgnoreStrategy.builder().dataPermission(true).build());
        } else {
            strategy.setDataPermission(true);
        }
    }

    static void disable() {
        Deque<Boolean> stack = DATA_PERMISSION_STACK.get();
        boolean previous = !stack.isEmpty() && stack.pop();
        IgnoreStrategy strategy = getIgnoreStrategy();
        if (ObjectUtil.isNotNull(strategy)) {
            if (previous) {
                strategy.setDataPermission(true);
            } else if (isOnlyDataPermissionIgnored(strategy) && stack.isEmpty()) {
                InterceptorIgnoreHelper.clearIgnoreStrategy();
            } else {
                strategy.setDataPermission(false);
            }
        }
        if (stack.isEmpty()) {
            DATA_PERMISSION_STACK.remove();
        }
    }

    private static IgnoreStrategy getIgnoreStrategy() {
        Object local = ReflectUtils.getStaticFieldValue(
            ReflectUtils.getField(InterceptorIgnoreHelper.class, "IGNORE_STRATEGY_LOCAL"));
        if (local instanceof ThreadLocal<?> threadLocal
            && threadLocal.get() instanceof IgnoreStrategy strategy) {
            return strategy;
        }
        return null;
    }

    private static boolean isOnlyDataPermissionIgnored(IgnoreStrategy strategy) {
        return !Boolean.TRUE.equals(strategy.getDynamicTableName())
            && !Boolean.TRUE.equals(strategy.getBlockAttack())
            && !Boolean.TRUE.equals(strategy.getIllegalSql())
            && !Boolean.TRUE.equals(strategy.getTenantLine())
            && CollectionUtil.isEmpty(strategy.getOthers());
    }
}
