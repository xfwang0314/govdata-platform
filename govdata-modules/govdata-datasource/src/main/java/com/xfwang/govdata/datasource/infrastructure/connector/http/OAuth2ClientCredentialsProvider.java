package com.xfwang.govdata.datasource.infrastructure.connector.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xfwang.govdata.datasource.domain.enums.ConnectionTestReason;
import com.xfwang.govdata.datasource.infrastructure.connector.support.ConnectionFailureException;
import com.xfwang.platform.api.secret.SecretProvider;
import com.xfwang.platform.api.secret.SecretType;
import com.xfwang.platform.api.secret.SecretValue;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** OAuth2 Client Credentials 实现，静态客户端凭据来自 SecretProvider。 */
@Component
public class OAuth2ClientCredentialsProvider implements AccessTokenProvider {

    private static final int MAX_TOKEN_RESPONSE_BYTES = 64 * 1024;
    private final List<SecretProvider> secretProviders;
    private final OAuth2TokenCache tokenCache;
    private final HttpTargetPolicy targetPolicy;
    private final ObjectMapper objectMapper;

    public OAuth2ClientCredentialsProvider(List<SecretProvider> secretProviders,
                                           OAuth2TokenCache tokenCache,
                                           HttpTargetPolicy targetPolicy,
                                           ObjectMapper objectMapper) {
        this.secretProviders = secretProviders;
        this.tokenCache = tokenCache;
        this.targetPolicy = targetPolicy;
        this.objectMapper = objectMapper;
    }

    @Override
    public OAuth2AccessToken acquire(OAuth2TokenRequest request) {
        String cacheKey = cacheKey(request);
        return tokenCache.getOrLoad(cacheKey, () -> requestToken(request));
    }

    @Override
    public OAuth2AccessToken refresh(OAuth2TokenRequest request) {
        String cacheKey = cacheKey(request);
        tokenCache.evict(cacheKey);
        return tokenCache.getOrLoad(cacheKey, () -> requestToken(request));
    }

    private OAuth2AccessToken requestToken(OAuth2TokenRequest request) {
        URI tokenEndpoint = targetPolicy.validate(request.tokenEndpoint(), true);
        Optional<SecretValue> resolved = secretProviders.stream()
            .map(provider -> provider.resolve(request.connectionContext().secretRequest(
                SecretType.OAUTH2_CLIENT, SecretType.OAUTH2_CLIENT.requiredKeys())))
            .flatMap(Optional::stream).findFirst();
        if (resolved.isEmpty()) {
            throw new ConnectionFailureException(ConnectionTestReason.SECRET_UNAVAILABLE);
        }
        try (SecretValue secret = resolved.orElseThrow()) {
            char[] clientId = secret.get("clientId");
            char[] clientSecret = secret.get("clientSecret");
            if (clientId == null || clientSecret == null) {
                throw new ConnectionFailureException(ConnectionTestReason.SECRET_FIELDS_MISSING);
            }
            try {
                return exchange(request, tokenEndpoint, clientId, clientSecret);
            } finally {
                Arrays.fill(clientId, '\0');
                Arrays.fill(clientSecret, '\0');
            }
        }
    }

    private OAuth2AccessToken exchange(OAuth2TokenRequest request, URI endpoint,
                                       char[] clientId, char[] clientSecret) {
        try {
            String form = form(request);
            String basic = Base64.getEncoder().encodeToString(
                (new String(clientId) + ":" + new String(clientSecret)).getBytes(StandardCharsets.UTF_8));
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(request.timeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER).build();
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(request.timeoutSeconds()))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
            HttpResponse<InputStream> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                byte[] content = body.readNBytes(MAX_TOKEN_RESPONSE_BYTES + 1);
                if (response.statusCode() < 200 || response.statusCode() >= 300
                    || content.length > MAX_TOKEN_RESPONSE_BYTES) {
                    throw failed();
                }
                Map<String, Object> payload = objectMapper.readValue(content,
                    new TypeReference<Map<String, Object>>() { });
                String accessToken = text(payload, "access_token");
                String tokenType = text(payload, "token_type");
                long expiresIn = number(payload, "expires_in");
                if (accessToken.isBlank() || !("bearer".equalsIgnoreCase(tokenType))
                    || expiresIn < 10 || expiresIn > 86_400) {
                    throw failed();
                }
                return new OAuth2AccessToken(accessToken, "Bearer", expiresIn);
            }
        } catch (ConnectionFailureException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failed();
        }
    }

    private String form(OAuth2TokenRequest request) {
        StringBuilder form = new StringBuilder("grant_type=client_credentials");
        append(form, "scope", request.scope());
        append(form, "audience", request.audience());
        return form.toString();
    }

    private void append(StringBuilder form, String name, String value) {
        if (value != null && !value.isBlank()) {
            form.append('&').append(name).append('=')
                .append(URLEncoder.encode(value.trim(), StandardCharsets.UTF_8));
        }
    }

    private String cacheKey(OAuth2TokenRequest request) {
        try {
            String material = request.connectionContext().getTenantId() + "|"
                + request.connectionContext().getSecretRef() + "|" + request.tokenEndpoint() + "|"
                + Optional.ofNullable(request.scope()).orElse("") + "|"
                + Optional.ofNullable(request.audience()).orElse("");
            return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("OAuth2 cache key unavailable", exception);
        }
    }

    private String text(Map<String, Object> payload, String key) {
        Object value = payload == null ? null : payload.get(key);
        return value == null ? "" : value.toString();
    }

    private long number(Map<String, Object> payload, String key) {
        try {
            return Long.parseLong(text(payload, key));
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private ConnectionFailureException failed() {
        return new ConnectionFailureException(ConnectionTestReason.OAUTH2_TOKEN_FAILED);
    }
}
