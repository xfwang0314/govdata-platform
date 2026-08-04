package com.xfwang.platform.auth.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 对具体资源执行动态授权校验。
 *
 * <p>Sa-Token 负责菜单和接口功能权限，本注解负责 Resource + Action 的动态授权。</p>
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ResourceAuthorize {

    /**
     * 资源主键的 SpEL 表达式，例如 {@code #tableId}。
     */
    String resourceId();

    /**
     * 预期的资源类型。留空时不校验资源类型。
     */
    String resourceType() default "";

    /**
     * 对资源执行的动作编码，例如 {@code QUERY}、{@code EXPORT}。
     */
    String action();

    /**
     * 访问用途的 SpEL 表达式，例如 {@code #request.purpose}。
     */
    String purpose() default "";
}
