package com.xfwang.platform.api.auth.annotation;

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
    String resourceId() default "";

    /**
     * 领域业务稳定 ID 的 SpEL 表达式。与 resourceId 必须且只能配置一个。
     */
    String businessId() default "";

    /**
     * 不做投影的类型资源上下文 SpEL，结果必须为可信构造的 ResourceDescriptor。
     * 与 resourceId、businessId 必须且只能配置一个。
     */
    String resourceContext() default "";

    /**
     * 使用 businessId 时必填，表示 Resource 的来源模块。
     */
    String sourceModule() default "";

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
