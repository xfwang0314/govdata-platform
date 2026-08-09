package com.xfwang.govdata.datasource.infrastructure.connector.http;

import com.xfwang.govdata.datasource.infrastructure.connector.support.ConnectorEndpointValidationContext;
import com.xfwang.govdata.datasource.infrastructure.connector.support.ConnectorEndpointValidator;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Set;
import java.util.regex.Pattern;

/** HTTP API Connector 的条件字段和安全配置校验。 */
@Component
public class HttpApiEndpointValidator implements ConnectorEndpointValidator {

    private static final Pattern HEADER_NAME = Pattern.compile("^[!#$%&'*+.^_`|~0-9A-Za-z-]{1,100}$");
    private static final Set<String> METHODS = Set.of("GET", "HEAD");
    private final HttpTargetPolicy targetPolicy;

    public HttpApiEndpointValidator(HttpTargetPolicy targetPolicy) {
        this.targetPolicy = targetPolicy;
    }

    @Override
    public boolean supports(String connectorCode, String connectorVersion) {
        return "http-api".equals(connectorCode) && "1.0".equals(connectorVersion);
    }

    @Override
    public void validate(ConnectorEndpointValidationContext context) {
        String baseUrl = text(context, "baseUrl");
        URI uri = configurationUri(baseUrl, "Base URL 格式无效");
        if ("PROD".equals(context.environment()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw invalid("生产环境 HTTP API 必须使用 HTTPS");
        }
        String path = text(context, "healthCheckPath");
        if (!path.startsWith("/") || path.contains("#") || path.length() > 500) {
            throw invalid("健康检查路径格式无效");
        }
        if (!METHODS.contains(text(context, "healthCheckMethod"))) {
            throw invalid("健康检查方法仅支持 GET 或 HEAD");
        }
        if ("API_KEY_HEADER".equals(context.authProfileCode())
            && !HEADER_NAME.matcher(text(context, "apiKeyHeader")).matches()) {
            throw invalid("API Key Header 名称无效");
        }
        if ("OAUTH2_CLIENT_CREDENTIALS".equals(context.authProfileCode())) {
            URI tokenUri = configurationUri(text(context, "tokenEndpoint"), "OAuth2 Token Endpoint 格式无效");
            if ("PROD".equals(context.environment()) && !"https".equalsIgnoreCase(tokenUri.getScheme())) {
                throw invalid("生产环境 OAuth2 Token Endpoint 必须使用 HTTPS");
            }
        }
    }

    private String text(ConnectorEndpointValidationContext context, String key) {
        Object value = context.endpointConfig().get(key);
        return value == null ? "" : value.toString().trim();
    }

    private ServiceException invalid(String message) {
        return new ServiceException(GlobalErrorCode.VALIDATION_ERROR, message);
    }

    private URI configurationUri(String value, String message) {
        try {
            return targetPolicy.validate(value, false);
        } catch (RuntimeException exception) {
            throw invalid(message);
        }
    }
}
