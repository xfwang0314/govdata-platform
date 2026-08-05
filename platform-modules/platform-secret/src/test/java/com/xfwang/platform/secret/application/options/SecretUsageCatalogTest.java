package com.xfwang.platform.secret.application.options;

import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretUsageCatalogTest {

    private final SecretUsageCatalog catalog = new SecretUsageCatalog();

    @Test
    void shouldExposeRegisteredModuleAndPurposes() {
        var module = catalog.ownerModules().get(0);

        assertThat(module.getValue()).isEqualTo("govdata-datasource");
        assertThat(module.getPurposes()).extracting("value")
            .containsExactly("DATASOURCE_CONNECTION_TEST", "DATASOURCE_QUERY");
    }

    @Test
    void shouldNormalizeRegisteredPurposes() {
        assertThat(catalog.normalizePurposeScope("govdata-datasource",
            " DATASOURCE_CONNECTION_TEST, DATASOURCE_QUERY "))
            .isEqualTo("DATASOURCE_CONNECTION_TEST,DATASOURCE_QUERY");
    }

    @Test
    void shouldRejectUnknownModuleOrPurpose() {
        assertThatThrownBy(() -> catalog.normalizePurposeScope("unknown", "DATASOURCE_QUERY"))
            .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> catalog.normalizePurposeScope("govdata-datasource", "CONNECTION_TEST"))
            .isInstanceOf(ServiceException.class);
    }
}
