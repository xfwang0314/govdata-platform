package com.xfwang.platform.secret.application.access;

import com.xfwang.platform.api.secret.SecretReferenceValidation;
import com.xfwang.platform.api.secret.SecretReferenceQuery;
import com.xfwang.platform.api.secret.SecretType;
import com.xfwang.platform.secret.domain.entity.PlatformSecret;
import com.xfwang.platform.secret.infrastructure.crypto.SecretCipherFacade;
import com.xfwang.platform.secret.infrastructure.persistence.mapper.PlatformSecretAccessLogMapper;
import com.xfwang.platform.secret.infrastructure.persistence.mapper.PlatformSecretMapper;
import com.xfwang.platform.secret.infrastructure.persistence.mapper.PlatformSecretVersionMapper;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("dev")
class SecretAccessServiceTest {

    @Test
    void listsAllCredentialTypesWhenCatalogQueryDoesNotSpecifyAType() {
        PlatformSecretMapper secretMapper = mock(PlatformSecretMapper.class);
        when(secretMapper.selectList(any())).thenReturn(java.util.List.of(
            secret("USERNAME_PASSWORD", "DATASOURCE_CONNECTION_TEST"),
            secret("TOKEN", "DATASOURCE_CONNECTION_TEST")));
        SecretAccessService service = new SecretAccessService(secretMapper, mock(PlatformSecretVersionMapper.class),
            mock(PlatformSecretAccessLogMapper.class), mock(SecretCipherFacade.class));

        var references = service.listAvailable(SecretReferenceQuery.builder()
            .callerModule("govdata-datasource").purpose("DATASOURCE_CONNECTION_TEST").build());

        assertThat(references).extracting("secretType")
            .containsExactly(SecretType.USERNAME_PASSWORD, SecretType.TOKEN);
    }

    @Test
    void rejectsCredentialWhoseTypeDoesNotMatchTheConnectorContract() {
        PlatformSecretMapper secretMapper = mock(PlatformSecretMapper.class);
        PlatformSecretVersionMapper versionMapper = mock(PlatformSecretVersionMapper.class);
        SecretCipherFacade cipher = mock(SecretCipherFacade.class);
        PlatformSecret secret = secret("USERNAME_PASSWORD", "DATASOURCE_CONNECTION_TEST");
        when(secretMapper.selectOne(any())).thenReturn(secret);
        SecretAccessService service = new SecretAccessService(secretMapper, versionMapper,
            mock(PlatformSecretAccessLogMapper.class), cipher);

        assertThatThrownBy(() -> service.validateReference(validation(SecretType.TOKEN)))
            .isInstanceOf(ServiceException.class).hasMessageContaining("不匹配");
    }

    private SecretReferenceValidation validation(SecretType type) {
        return SecretReferenceValidation.builder().secretRef("secret:sec_1")
            .callerModule("govdata-datasource").secretType(type)
            .purpose("DATASOURCE_CONNECTION_TEST").requiredKeys(Set.of("username", "password")).build();
    }

    private PlatformSecret secret(String type, String purpose) {
        PlatformSecret secret = new PlatformSecret();
        secret.setTenantId("000000");
        secret.setSecretId(1L);
        secret.setSecretRef("secret:sec_1");
        secret.setSecretType(type);
        secret.setOwnerModule("govdata-datasource");
        secret.setPurposeScope(purpose);
        secret.setStatus("ACTIVE");
        secret.setExpireTime(LocalDateTime.now().plusDays(1));
        secret.setCurrentVersionId(10L);
        return secret;
    }
}
