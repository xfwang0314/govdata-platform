package com.xfwang.platform.secret.application.management;

import com.xfwang.platform.secret.application.management.command.SecretCreateCommand;
import com.xfwang.platform.secret.application.management.command.SecretUpdateCommand;
import com.xfwang.platform.secret.application.management.view.SecretView;
import com.xfwang.platform.secret.application.options.SecretUsageCatalog;
import com.xfwang.platform.secret.domain.entity.PlatformSecret;
import com.xfwang.platform.secret.infrastructure.crypto.AesGcmSecretCipher;
import com.xfwang.platform.secret.infrastructure.persistence.mapper.PlatformSecretMapper;
import com.xfwang.platform.secret.infrastructure.persistence.mapper.PlatformSecretVersionMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecretDescriptionContractTest {

    @Test
    void shouldExposeDescriptionAcrossManagementContract() {
        assertDescriptionField(PlatformSecret.class);
        assertDescriptionField(SecretCreateCommand.class);
        assertDescriptionField(SecretUpdateCommand.class);
        assertDescriptionField(SecretView.class);
    }

    @Test
    void shouldTrimAndReturnDescriptionWhenUpdatingMetadata() {
        PlatformSecretMapper secretMapper = mock(PlatformSecretMapper.class);
        PlatformSecretVersionMapper versionMapper = mock(PlatformSecretVersionMapper.class);
        SecretUsageCatalog usageCatalog = mock(SecretUsageCatalog.class);
        SecretManagementService service = new SecretManagementService(secretMapper, versionMapper,
            mock(AesGcmSecretCipher.class), usageCatalog);
        PlatformSecret secret = new PlatformSecret();
        secret.setSecretId(1L);
        secret.setSecretName("old");
        secret.setOwnerModule("govdata-datasource");
        secret.setPurposeScope("DATASOURCE_QUERY");
        secret.setLockVersion(3);
        when(secretMapper.selectById(1L)).thenReturn(secret);
        when(secretMapper.updateById(secret)).thenReturn(1);
        when(usageCatalog.normalizePurposeScope("govdata-datasource", "DATASOURCE_QUERY"))
            .thenReturn("DATASOURCE_QUERY");
        SecretUpdateCommand command = new SecretUpdateCommand();
        command.setSecretName("credential");
        command.setDescription("  数据治理处维护  ");
        command.setPurposeScope("DATASOURCE_QUERY");
        command.setLockVersion(3);

        SecretView view = service.update(1L, command);

        assertThat(secret.getDescription()).isEqualTo("数据治理处维护");
        assertThat(view.getDescription()).isEqualTo("数据治理处维护");
    }

    private void assertDescriptionField(Class<?> type) {
        assertThat(Arrays.stream(type.getDeclaredFields()).map(Field::getName))
            .as("%s should declare description", type.getSimpleName())
            .contains("description");
    }
}
