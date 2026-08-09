package com.xfwang.govdata.datasource.infrastructure.connector.redis;

import com.xfwang.govdata.datasource.domain.connector.ConnectorDescriptor;
import com.xfwang.govdata.datasource.domain.enums.ConnectionTestReason;
import com.xfwang.govdata.datasource.infrastructure.connector.model.ConnectionTestRequest;
import com.xfwang.govdata.datasource.infrastructure.connector.model.ConnectionTestResult;
import com.xfwang.platform.api.secret.SecretResolveRequest;
import com.xfwang.platform.api.secret.SecretType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class RedisConnectionTesterTest {

    private final RedisConnectionTester tester = new RedisConnectionTester(List.of());

    @Test
    void declaresSingleNodeAclConnectorContract() {
        ConnectorDescriptor descriptor = tester.descriptor();

        assertThat(descriptor.getConnectorCode()).isEqualTo("redis");
        assertThat(descriptor.getCategory()).isEqualTo("KEY_VALUE");
        assertThat(descriptor.getConnectionPreview().getTemplate())
            .isEqualTo("{protocol}://{host}:{port}/{database}");
        assertThat(descriptor.getEndpointFields()).extracting("code")
            .containsExactly("protocol", "host", "port", "database");
        assertThat(descriptor.getAuthProfiles()).extracting("profileCode", "secretType")
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("ACL_PASSWORD", SecretType.USERNAME_PASSWORD),
                org.assertj.core.groups.Tuple.tuple("PASSWORD", SecretType.PASSWORD));
        assertThat(descriptor.getAuthProfiles().get(0).getRequiredKeys())
            .containsExactlyInAnyOrder("username", "password");
        assertThat(descriptor.getAuthProfiles().get(1).getRequiredKeys()).containsExactly("password");
    }

    @Test
    void reportsUnavailableSecretBeforeOpeningRedisConnection() {
        ConnectionTestResult result = tester.test(ConnectionTestRequest.builder()
            .connectorCode("redis").connectorVersion("1.0")
            .authProfileCode("ACL_PASSWORD")
            .endpointConfig(Map.of("protocol", "redis", "host", "127.0.0.1", "port", 6379, "database", 0))
            .secretRef("secret:missing").build());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getReasonCode()).isEqualTo("SECRET_UNAVAILABLE");
    }

    @Test
    void mapsLegacyPasswordAuthenticationToPasswordSecretContract() {
        AtomicReference<SecretResolveRequest> captured = new AtomicReference<>();
        RedisConnectionTester passwordTester = new RedisConnectionTester(List.of(request -> {
            captured.set(request);
            return Optional.empty();
        }));

        passwordTester.test(ConnectionTestRequest.builder()
            .connectorCode("redis").connectorVersion("1.0").authProfileCode("PASSWORD")
            .endpointConfig(Map.of("protocol", "redis", "host", "127.0.0.1", "port", 6379, "database", 0))
            .secretRef("secret:missing").build());

        assertThat(captured.get().getExpectedSecretType()).isEqualTo(SecretType.PASSWORD);
        assertThat(captured.get().getRequiredKeys()).containsExactly("password");
    }
}
