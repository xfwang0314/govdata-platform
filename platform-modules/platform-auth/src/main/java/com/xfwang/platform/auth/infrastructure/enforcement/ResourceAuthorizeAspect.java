package com.xfwang.platform.auth.infrastructure.enforcement;

import com.xfwang.platform.api.auth.annotation.ResourceAuthorize;
import com.xfwang.platform.api.auth.model.AccessDecision;
import com.xfwang.platform.api.auth.model.AccessRequest;
import com.xfwang.platform.api.auth.model.EnvironmentContext;
import com.xfwang.platform.api.auth.model.ResourceDescriptor;
import com.xfwang.platform.api.auth.service.AuthorizationService;
import com.xfwang.platform.api.auth.service.ResourceRegistry;
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
    private final ResourceRegistry resourceRegistry;
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
        ResolvedResource resolvedResource = resolveResource(annotation, evaluationContext);
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
            .resourceId(resolvedResource.resourceId())
            .resource(resolvedResource.resource())
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

    private ResolvedResource resolveResource(ResourceAuthorize annotation,
                                             MethodBasedEvaluationContext evaluationContext) {
        boolean idConfigured = StringUtils.isNotBlank(annotation.resourceId());
        boolean businessConfigured = StringUtils.isNotBlank(annotation.businessId());
        boolean contextConfigured = StringUtils.isNotBlank(annotation.resourceContext());
        if ((idConfigured ? 1 : 0) + (businessConfigured ? 1 : 0) + (contextConfigured ? 1 : 0) != 1) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR,
                "resourceId、businessId 与 resourceContext 必须且只能配置一个");
        }
        if (idConfigured) {
            return new ResolvedResource(toLong(expressionParser.parseExpression(annotation.resourceId())
                .getValue(evaluationContext)), null);
        }
        if (contextConfigured) {
            Object value = expressionParser.parseExpression(annotation.resourceContext())
                .getValue(evaluationContext);
            if (!(value instanceof ResourceDescriptor descriptor)) {
                throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR,
                    "resourceContext 必须解析为 ResourceDescriptor");
            }
            if (StringUtils.isNotBlank(annotation.resourceType())
                && !StringUtils.equals(annotation.resourceType(), descriptor.getResourceType())) {
                throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "资源上下文类型不匹配");
            }
            return new ResolvedResource(null, descriptor);
        }
        if (StringUtils.isAnyBlank(annotation.sourceModule(), annotation.resourceType())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR,
                "businessId 授权必须声明 sourceModule 和 resourceType");
        }
        Object businessId = expressionParser.parseExpression(annotation.businessId())
            .getValue(evaluationContext);
        if (businessId == null || businessId.toString().isBlank()) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "无法解析授权资源业务标识");
        }
        Long resourceId = resourceRegistry.findByBusinessKey(annotation.sourceModule(), annotation.resourceType(),
                businessId.toString())
            .map(com.xfwang.platform.api.auth.model.ResourceDescriptor::getResourceId)
            .orElseThrow(() -> new ServiceException(GlobalErrorCode.FORBIDDEN, "授权资源投影尚未就绪"));
        return new ResolvedResource(resourceId, null);
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

    private record ResolvedResource(Long resourceId, ResourceDescriptor resource) {
    }
}
