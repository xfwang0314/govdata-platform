package com.xfwang.govdata.datasource.infrastructure.connector.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.xfwang.govdata.datasource.infrastructure.connector.model.ConnectionTestRequest;
import com.xfwang.platform.api.secret.SecretProvider;
import com.xfwang.platform.api.secret.SecretValue;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2ClientCredentialsProviderTest {

    @Test
    void exchangesClientCredentialForShortLivedBearerToken() throws Exception {
        String expectedBasic = "Basic " + Base64.getEncoder()
            .encodeToString("client:secret".getBytes(StandardCharsets.UTF_8));
        try (TestTokenServer server = new TestTokenServer(expectedBasic)) {
            SecretProvider secrets = request -> Optional.of(new SecretValue(Map.of(
                "clientId", "client".toCharArray(), "clientSecret", "secret".toCharArray())));
            OAuth2ClientCredentialsProvider provider = new OAuth2ClientCredentialsProvider(
                List.of(secrets), new NoCache(), localPolicy(), new ObjectMapper());
            ConnectionTestRequest context = ConnectionTestRequest.builder().tenantId("000000")
                .secretRef("secret:sec_oauth").actorType("USER").actorId("1")
                .purpose("DATASOURCE_CONNECTION_TEST").build();

            OAuth2AccessToken token = provider.acquire(new OAuth2TokenRequest(
                context, server.tokenEndpoint(), "read write", "govdata", 3));

            assertThat(token.value()).isEqualTo("access-token");
            assertThat(token.tokenType()).isEqualTo("Bearer");
            assertThat(token.expiresInSeconds()).isEqualTo(300);
        }
    }

    private HttpTargetPolicy localPolicy() {
        return new HttpTargetPolicy() {
            @Override
            public URI validate(String value, boolean resolveHost) {
                return URI.create(value);
            }
        };
    }

    private static final class NoCache implements OAuth2TokenCache {
        @Override
        public OAuth2AccessToken getOrLoad(String cacheKey, TokenLoader loader) {
            return loader.load();
        }

        @Override
        public void evict(String cacheKey) {
        }
    }

    private static final class TestTokenServer implements AutoCloseable {
        private final HttpServer server;

        private TestTokenServer(String expectedBasic) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/token", exchange -> {
                byte[] request = exchange.getRequestBody().readAllBytes();
                boolean valid = expectedBasic.equals(exchange.getRequestHeaders().getFirst("Authorization"))
                    && new String(request, StandardCharsets.UTF_8).contains("grant_type=client_credentials");
                byte[] response = (valid
                    ? "{\"access_token\":\"access-token\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                    : "{}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(valid ? 200 : 401, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();
        }

        private String tokenEndpoint() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/token";
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
