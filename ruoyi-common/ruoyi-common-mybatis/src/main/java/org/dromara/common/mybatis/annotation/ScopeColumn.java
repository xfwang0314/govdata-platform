package org.dromara.common.mybatis.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** ABAC 逻辑属性到受信任 SQL 列的映射。 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ScopeColumn {

    /** 结构化范围属性，例如 ownerDeptId、ownerUserId。 */
    String attribute();

    /** 当前 Mapper SQL 中的列或带别名列，只允许代码常量声明。 */
    String column();
}
