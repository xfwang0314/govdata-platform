package org.dromara.common.mybatis.aspect;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.dromara.common.mybatis.annotation.AbacDataScope;
import org.dromara.common.mybatis.helper.AbacDataScopeHelper;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** 将 Mapper 上的 ABAC 范围声明绑定到当前执行上下文。 */
public class AbacDataScopeAdvice implements MethodInterceptor {

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        AbacDataScope previous = AbacDataScopeHelper.getScope();
        AbacDataScopeHelper.setScope(findScope(invocation.getThis(), invocation.getMethod()));
        try {
            return invocation.proceed();
        } finally {
            if (previous == null) {
                AbacDataScopeHelper.removeScope();
            } else {
                AbacDataScopeHelper.setScope(previous);
            }
        }
    }

    private AbacDataScope findScope(Object target, Method method) {
        AbacDataScope scope = method.getAnnotation(AbacDataScope.class);
        if (scope != null) {
            return scope;
        }
        Class<?> targetClass = target.getClass();
        if (Proxy.isProxyClass(targetClass)) {
            targetClass = targetClass.getInterfaces()[0];
        }
        return targetClass.getAnnotation(AbacDataScope.class);
    }
}
