package org.dromara.common.mybatis.aspect;

import org.aopalliance.aop.Advice;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;

/** ABAC 数据范围切面定义。 */
public class AbacDataScopePointcutAdvisor extends AbstractPointcutAdvisor {

    private final Advice advice = new AbacDataScopeAdvice();
    private final Pointcut pointcut = new AbacDataScopePointcut();

    @Override
    public Pointcut getPointcut() {
        return pointcut;
    }

    @Override
    public Advice getAdvice() {
        return advice;
    }
}
