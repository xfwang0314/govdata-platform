package com.xfwang.govdata.datasource.infrastructure.connector.http;

import com.sun.net.httpserver.HttpServer;
import com.xfwang.govdata.datasource.infrastructure.connector.model.ConnectionTestRequest;
import com.xfwang.govdata.datasource.infrastructure.connector.model.ConnectionTestResult;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class HttpApiConnectionTesterTest {

    @Test
    void testsAnonymousHttpEndpointWithoutSecret() throws Exception {
        try (TestHttpServer server = new TestHttpServer(exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        })) {
            HttpApiConnectionTester tester = new HttpApiConnectionTester(List.of(), tokenProvider(), localPolicy());

            ConnectionTestResult result = tester.test(request(server.baseUrl(), "NONE"));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getServerInfo()).containsEntry("statusCode", 204);
        }
    }

    @Test
    void refreshesOAuth2TokenOnceAfterUnauthorizedResponse() throws Exception {
        AtomicInteger refreshes = new AtomicInteger();
        try (TestHttpServer server = new TestHttpServer(exchange -> {
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            int status = "Bearer refreshed".equals(authorization) ? 204 : 401;
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        })) {
            AccessTokenProvider tokens = new AccessTokenProvider() {
                @Override
                public OAuth2AccessToken acquire(OAuth2TokenRequest request) {
                    return new OAuth2AccessToken("cached", "Bearer", 300);
                }

                @Override
                public OAuth2AccessToken refresh(OAuth2TokenRequest request) {
                    refreshes.incrementAndGet();
                    return new OAuth2AccessToken("refreshed", "Bearer", 300);
                }
            };
            HttpApiConnectionTester tester = new HttpApiConnectionTester(List.of(), tokens, localPolicy());

            ConnectionTestResult result = tester.test(request(server.baseUrl(), "OAUTH2_CLIENT_CREDENTIALS"));

            assertThat(result.isSuccess()).isTrue();
            assertThat(refreshes).hasValue(1);
        }
    }

    private ConnectionTestRequest request(String baseUrl, String authProfile) {
        return ConnectionTestRequest.builder().connectorCode("http-api").connectorVersion("1.0")
            .authProfileCode(authProfile).tenantId("000000").actorType("USER").actorId("1")
            .purpose("DATASOURCE_CONNECTION_TEST").endpointConfig(Map.of(
                "baseUrl", baseUrl, "healthCheckPath", "/health", "healthCheckMethod", "GET",
                "connectTimeoutSeconds", 3, "readTimeoutSeconds", 3,
                "tokenEndpoint", baseUrl + "/token", "scope", "", "audience", ""))
            .build();
    }

    private AccessTokenProvider tokenProvider() {
        return new AccessTokenProvider() {
            @Override
            public OAuth2AccessToken acquire(OAuth2TokenRequest request) {
                return new OAuth2AccessToken("unused", "Bearer", 300);
            }

            @Override
            public OAuth2AccessToken refresh(OAuth2TokenRequest request) {
                return acquire(request);
            }
        };
    }

    private HttpTargetPolicy localPolicy() {
        return new HttpTargetPolicy() {
            @Override
            public URI validate(String value, boolean resolveHost) {
                return URI.create(value);
            }
        };
    }

    private static final class TestHttpServer implements AutoCloseable {
        private final HttpServer server;

        private TestHttpServer(com.sun.net.httpserver.HttpHandler handler) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/health", handler);
            server.start();
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
