package org.dromara.common.json.enhance;

import com.fasterxml.jackson.databind.introspect.AnnotatedMember;

import java.lang.annotation.Annotation;

/**
 * JSON 响应字段上下文。
 */
public record JsonFieldContext(Object owner, String propertyName, AnnotatedMember member, Object value) {

    public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
        return member == null ? null : member.getAnnotation(annotationType);
    }
}
