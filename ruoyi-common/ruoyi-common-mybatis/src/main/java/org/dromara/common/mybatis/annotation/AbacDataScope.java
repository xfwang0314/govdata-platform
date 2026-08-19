package org.dromara.common.mybatis.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明平台控制库查询需要执行 ABAC 结构化数据范围。
 * 资源类型和受信任列映射必须显式声明，禁止授权模块返回任意 SQL。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AbacDataScope {

    /** 授权目录中的稳定资源类型。 */
    String resourceType();

    /** 授权目录中的稳定动作，必须显式声明。 */
    String action();

    /** 授权目录中的稳定访问目的，必须显式声明。 */
    String purpose();

    /** ABAC 逻辑属性到当前 SQL 实际列的受信任映射。 */
    ScopeColumn[] columns();
}
