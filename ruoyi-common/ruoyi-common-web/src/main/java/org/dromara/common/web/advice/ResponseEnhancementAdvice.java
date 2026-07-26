package org.dromara.common.web.advice;

import lombok.RequiredArgsConstructor;
import org.dromara.common.json.enhance.JsonValueEnhancer;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 出站 JSON 统一增强入口。
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class ResponseEnhancementAdvice implements ResponseBodyAdvice<Object> {

    private final JsonValueEnhancer jsonValueEnhancer;

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return jsonValueEnhancer.supports(converterType);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (!selectedContentType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
            return body;
        }
        return jsonValueEnhancer.enhance(body);
    }
}
