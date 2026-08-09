package com.xfwang.govdata.datasource.infrastructure.connector.http;

import com.xfwang.govdata.datasource.domain.connector.ConnectorAuthProfile;
import com.xfwang.govdata.datasource.domain.connector.ConnectorDescriptor;
import com.xfwang.govdata.datasource.domain.connector.ConnectorFieldDescriptor;
import com.xfwang.govdata.datasource.domain.connector.ConnectorPreviewDescriptor;
import com.xfwang.govdata.datasource.domain.enums.ConnectionTestReason;
import com.xfwang.govdata.datasource.infrastructure.connector.AbstractConnectionTester;
import com.xfwang.govdata.datasource.infrastructure.connector.ConnectorDescriptorProvider;
import com.xfwang.govdata.datasource.infrastructure.connector.model.ConnectionTestRequest;
import com.xfwang.govdata.datasource.infrastructure.connector.model.ConnectionTestResult;
import com.xfwang.govdata.datasource.infrastructure.connector.support.ConnectionFailureClassifier;
import com.xfwang.govdata.datasource.infrastructure.connector.support.ConnectionFailureException;
import com.xfwang.platform.api.secret.SecretProvider;
import com.xfwang.platform.api.secret.SecretType;
import com.xfwang.platform.api.secret.SecretValue;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.xfwang.govdata.datasource.infrastructure.connector.ConnectorDescriptorSupport.authTextField;
import static com.xfwang.govdata.datasource.infrastructure.connector.ConnectorDescriptorSupport.choiceField;
import static com.xfwang.govdata.datasource.infrastructure.connector.ConnectorDescriptorSupport.integerField;
import static com.xfwang.govdata.datasource.infrastructure.connector.ConnectorDescriptorSupport.textField;

/**
 * HTTP API 连通与认证测试 Connector。
 *
 * <p>继承自 {@link AbstractConnectionTester}，支持 NONE, BASIC, BEARER_TOKEN, API_KEY_HEADER 以及 OAUTH2_CLIENT_CREDENTIALS 认证类型的 HTTP API 节点测试。</p>
 */
@Component
public class HttpApiConnectionTester extends AbstractConnectionTester implements ConnectorDescriptorProvider {

    private final AccessTokenProvider accessTokenProvider;
    private final HttpTargetPolicy targetPolicy;

    /**
     * 构造函数，注入凭据提供者、OAuth2 Token 提供者及 HTTP 目标安全策略。
     *
     * @param secretProviders     凭据解析提供者列表
     * @param accessTokenProvider OAuth2 Token 缓存提供者
     * @param targetPolicy        HTTP URL 安全策略
     */
    public HttpApiConnectionTester(List<SecretProvider> secretProviders,
                                  AccessTokenProvider accessTokenProvider,
                                  HttpTargetPolicy targetPolicy) {
        super(secretProviders);
        this.accessTokenProvider = accessTokenProvider;
        this.targetPolicy = targetPolicy;
    }

    @Override
    protected String connectorCode() {
        return "http-api";
    }

    @Override
    protected String connectorVersion() {
        return "1.0";
    }

    /**
     * 执行 HTTP API 健康检查端点连通与认证测试。
     *
     * @param request 连通测试请求
     * @return 连通测试结果
     */
    @Override
    public ConnectionTestResult test(ConnectionTestRequest request) {
        long started = System.nanoTime();
        try {
            URI base = targetPolicy.validate(text(request, "baseUrl"), true);
            URI target = base.resolve(text(request, "healthCheckPath"));
            targetPolicy.validate(target.toString(), true);
            int connectTimeout = integer(request, "connectTimeoutSeconds");
            int readTimeout = integer(request, "readTimeoutSeconds");
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeout))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
            HttpResponse<Void> response = send(client, target, request, readTimeout, false);
            if (response.statusCode() == 401
                && "OAUTH2_CLIENT_CREDENTIALS".equals(request.getAuthProfileCode())) {
                response = send(client, target, request, readTimeout, true);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 400) {
                ConnectionTestReason reason = Set.of(401, 403).contains(response.statusCode())
                    ? ConnectionTestReason.AUTHENTICATION_FAILED : ConnectionTestReason.HTTP_STATUS_REJECTED;
                return failed(reason, null, elapsed(started), Map.of("statusCode", response.statusCode()));
            }
            return ConnectionTestResult.builder()
                .success(true)
                .reasonCode(ConnectionTestReason.CONNECTED.code())
                .message(ConnectionTestReason.CONNECTED.message())
                .latencyMs(elapsed(started))
                .serverInfo(Map.of("statusCode", response.statusCode(), "protocol", base.getScheme().toUpperCase()))
                .build();
        } catch (ConnectionFailureException exception) {
            return failed(exception.reason(), null, elapsed(started), Map.of());
        } catch (Exception exception) {
            return failed(ConnectionFailureClassifier.classify(exception), null, elapsed(started), Map.of());
        }
    }

    /**
     * 构建并发送 HTTP 请求。
     */
    private HttpResponse<Void> send(HttpClient client, URI target, ConnectionTestRequest request,
                                    int readTimeout, boolean refreshToken) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(target)
            .timeout(Duration.ofSeconds(readTimeout))
            .header("Accept", "application/json, */*;q=0.1");
        applyAuthentication(request, builder, refreshToken);
        HttpRequest httpRequest = "HEAD".equals(text(request, "healthCheckMethod"))
            ? builder.method("HEAD", HttpRequest.BodyPublishers.noBody()).build()
            : builder.GET().build();
        return client.send(httpRequest, HttpResponse.BodyHandlers.discarding());
    }

    /**
     * 根据连通请求注入相应的 Authorization 或 Key 请求头。
     */
    private void applyAuthentication(ConnectionTestRequest request, HttpRequest.Builder builder,
                                      boolean refreshToken) {
        switch (request.getAuthProfileCode()) {
            case "NONE" -> { }
            case "BASIC" -> withSecret(request, SecretType.USERNAME_PASSWORD, secret -> {
                char[] username = required(secret, "username");
                char[] password = required(secret, "password");
                try {
                    String basic = Base64.getEncoder().encodeToString(
                        (new String(username) + ":" + new String(password)).getBytes(StandardCharsets.UTF_8));
                    builder.header("Authorization", "Basic " + basic);
                } finally {
                    Arrays.fill(username, '\0');
                    Arrays.fill(password, '\0');
                }
            });
            case "BEARER_TOKEN" -> withSecret(request, SecretType.TOKEN, secret -> {
                char[] token = required(secret, "token");
                try {
                    builder.header("Authorization", "Bearer " + new String(token));
                } finally {
                    Arrays.fill(token, '\0');
                }
            });
            case "API_KEY_HEADER" -> withSecret(request, SecretType.API_KEY, secret -> {
                char[] apiKey = required(secret, "apiKey");
                try {
                    builder.header(text(request, "apiKeyHeader"), new String(apiKey));
                } finally {
                    Arrays.fill(apiKey, '\0');
                }
            });
            case "OAUTH2_CLIENT_CREDENTIALS" -> {
                OAuth2TokenRequest tokenRequest = new OAuth2TokenRequest(request,
                    text(request, "tokenEndpoint"), text(request, "scope"), text(request, "audience"),
                    integer(request, "connectTimeoutSeconds"));
                OAuth2AccessToken token = refreshToken
                    ? accessTokenProvider.refresh(tokenRequest) : accessTokenProvider.acquire(tokenRequest);
                builder.header("Authorization", token.tokenType() + " " + token.value());
            }
            default -> throw new ConnectionFailureException(ConnectionTestReason.ENDPOINT_INVALID);
        }
    }

    /**
     * 辅助消费凭据。
     */
    private void withSecret(ConnectionTestRequest request, SecretType type,
                            java.util.function.Consumer<SecretValue> consumer) {
        Optional<SecretValue> resolved = resolveSecret(request, type, type.requiredKeys());
        if (resolved.isEmpty()) {
            throw new ConnectionFailureException(ConnectionTestReason.SECRET_UNAVAILABLE);
        }
        try (SecretValue secret = resolved.orElseThrow()) {
            consumer.accept(secret);
        }
    }

    /**
     * 校验必填凭据字段。
     */
    private char[] required(SecretValue secret, String key) {
        char[] value = secret.get(key);
        if (value == null || value.length == 0) {
            throw new ConnectionFailureException(ConnectionTestReason.SECRET_FIELDS_MISSING);
        }
        return value;
    }

    /**
     * 扩展重载的失败构造函数，附加服务器附加信息。
     */
    private ConnectionTestResult failed(ConnectionTestReason reason, String message, long latencyMs, Map<String, Object> info) {
        return ConnectionTestResult.builder()
            .success(false)
            .reasonCode(reason.code())
            .message(message == null ? reason.message() : message)
            .latencyMs(latencyMs)
            .serverInfo(info)
            .build();
    }

    /**
     * 提供 HTTP API Connector 元数据描述符。
     *
     * @return Connector 描述符
     */
    @Override
    public ConnectorDescriptor descriptor() {
        return ConnectorDescriptor.builder()
            .connectorCode("http-api")
            .connectorName("HTTP API")
            .connectorVersion("1.0")
            .category("API")
            .capabilities(Set.of("CONNECTION_TEST"))
            .connectionPreview(ConnectorPreviewDescriptor.builder()
                .label("API Base URL")
                .template("{baseUrl}")
                .build())
            .endpointFields(List.of(
                textField("baseUrl", "Base URL", true, "HTTP API 基础地址；生产环境必须使用 HTTPS"),
                field("healthCheckPath", "健康检查路径", "STRING", true, "/", null, null, List.of(), Set.of()),
                choiceField("healthCheckMethod", "健康检查方法", "GET", List.of("GET", "HEAD")),
                integerField("connectTimeoutSeconds", "连接超时（秒）", true, 5, 1, 30),
                integerField("readTimeoutSeconds", "响应超时（秒）", true, 10, 1, 60),
                authTextField("apiKeyHeader", "API Key Header", true, "例如 X-API-Key", "API_KEY_HEADER"),
                authTextField("tokenEndpoint", "Token Endpoint", true, "OAuth2 Token 获取地址", "OAUTH2_CLIENT_CREDENTIALS"),
                authTextField("scope", "OAuth2 Scope", false, "多个 Scope 使用空格分隔", "OAUTH2_CLIENT_CREDENTIALS"),
                authTextField("audience", "OAuth2 Audience", false, "授权服务器要求时填写", "OAUTH2_CLIENT_CREDENTIALS")))
            .authProfiles(List.of(
                profile("NONE", "无认证", null),
                profile("BASIC", "Basic 用户名和密码", SecretType.USERNAME_PASSWORD),
                profile("BEARER_TOKEN", "Bearer Token", SecretType.TOKEN),
                profile("API_KEY_HEADER", "API Key Header", SecretType.API_KEY),
                profile("OAUTH2_CLIENT_CREDENTIALS", "OAuth2 Client Credentials", SecretType.OAUTH2_CLIENT)))
            .build();
    }

    private ConnectorAuthProfile profile(String code, String name, SecretType type) {
        return ConnectorAuthProfile.builder().profileCode(code).profileName(name).secretType(type).build();
    }

    private ConnectorFieldDescriptor field(String code, String label, String type, boolean required,
                                           Object defaultValue, Integer min, Integer max,
                                           List<String> options, Set<String> authProfiles) {
        return ConnectorFieldDescriptor.builder()
            .code(code)
            .label(label)
            .valueType(type)
            .required(required)
            .defaultValue(defaultValue)
            .minValue(min)
            .maxValue(max)
            .options(options)
            .authProfileCodes(authProfiles)
            .build();
    }
}
