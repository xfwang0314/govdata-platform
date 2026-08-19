package org.dromara.common.mybatis.aspect;

import org.dromara.common.mybatis.annotation.AbacDataScope;
import org.springframework.aop.support.StaticMethodMatcherPointcut;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** 匹配声明了 {@link AbacDataScope} 的 Mapper 方法或接口。 */
public class AbacDataScopePointcut extends StaticMethodMatcherPointcut {

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        if (method.isAnnotationPresent(AbacDataScope.class)) {
            return true;
        }
        Class<?> inspected = targetClass;
        if (Proxy.isProxyClass(inspected)) {
            inspected = inspected.getInterfaces()[0];
        }
        return inspected.isAnnotationPresent(AbacDataScope.class);
    }
}
