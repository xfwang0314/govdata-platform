package com.xfwang.platform.auth.infrastructure.enforcement;

import com.xfwang.platform.api.auth.annotation.ResourceAuthorize;
import com.xfwang.platform.api.auth.model.AccessDecision;
import com.xfwang.platform.api.auth.model.AccessRequest;
import com.xfwang.platform.api.auth.model.EnvironmentContext;
import com.xfwang.platform.api.auth.service.AuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.ServletUtils;
import org.dromara.common.core.utils.StringUtils;
import org.slf4j.MDC;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * Resource + Action 授权执行点。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class ResourceAuthorizeAspect {

    public static final String TRUSTED_NETWORK_ZONE_ATTRIBUTE = "authz.networkZone";

    private final AuthorizationService authorizationService;
    private final ThreadLocalAuthorizationContextAccessor contextAccessor;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer =
        new DefaultParameterNameDiscoverer();

    @Around("@annotation(com.xfwang.platform.api.auth.annotation.ResourceAuthorize)"
        + " || @within(com.xfwang.platform.api.auth.annotation.ResourceAuthorize)")
    public Object authorize(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = AopUtils.getMostSpecificMethod(signature.getMethod(),
            joinPoint.getTarget().getClass());
        ResourceAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(
            method, ResourceAuthorize.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(
                joinPoint.getTarget().getClass(), ResourceAuthorize.class);
        }
        if (annotation == null) {
            return joinPoint.proceed();
        }

        MethodBasedEvaluationContext evaluationContext = new MethodBasedEvaluationContext(
            joinPoint.getTarget(), method, joinPoint.getArgs(), parameterNameDiscoverer);
        Long resourceId = toLong(expressionParser.parseExpression(annotation.resourceId())
            .getValue(evaluationContext));
        String purpose = evaluatePurpose(annotation.purpose(), evaluationContext);
        HttpServletRequest servletRequest = ServletUtils.getRequest();
        EnvironmentContext environment = EnvironmentContext.builder()
            .purpose(purpose)
            .requestTime(LocalDateTime.now())
            .ip(servletRequest == null ? null : ServletUtils.getClientIP(servletRequest))
            .networkZone(trustedNetworkZone(servletRequest))
            .traceId(MDC.get("traceId"))
            .build();
        AccessRequest request = AccessRequest.builder()
            .resourceId(resourceId)
            .resourceType(annotation.resourceType())
            .action(annotation.action())
            .environment(environment)
            .build();
        AccessDecision decision = authorizationService.decide(request);
        if (!decision.isAllowed()) {
            throw new ServiceException(GlobalErrorCode.FORBIDDEN,
                decision.getReasonCode() + ": " + decision.getReason());
        }
        contextAccessor.push(request, decision);
        try {
            return joinPoint.proceed();
        } finally {
            contextAccessor.pop();
        }
    }

    private String evaluatePurpose(String purpose, MethodBasedEvaluationContext context) {
        if (StringUtils.isBlank(purpose)) {
            return null;
        }
        if (!purpose.startsWith("#")) {
            return purpose;
        }
        Object value = expressionParser.parseExpression(purpose).getValue(context);
        return value == null ? null : value.toString();
    }

    private String trustedNetworkZone(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object value = request.getAttribute(TRUSTED_NETWORK_ZONE_ATTRIBUTE);
        return value == null ? null : value.toString();
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException ignored) {
                // 统一转换为参数错误。
            }
        }
        throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "无法解析授权资源主键");
    }
}
