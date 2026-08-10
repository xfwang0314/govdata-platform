package com.xfwang.platform.secret.application.options;

import com.xfwang.platform.api.secret.SecretPurposeDefinition;
import com.xfwang.platform.api.secret.SecretUsageContributor;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretUsageCatalogTest {

    private final SecretUsageCatalog catalog = new SecretUsageCatalog(List.of(contributor()));

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

    @Test
    void shouldRejectDuplicateOwnerModuleContributors() {
        assertThatThrownBy(() -> new SecretUsageCatalog(List.of(contributor(), contributor())))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("重复凭据消费模块编码");
    }

    @Test
    void shouldRejectDuplicatePurposeCodesInsideAContributor() {
        SecretUsageContributor invalid = new SecretUsageContributor() {
            public String ownerModule() { return "module-a"; }
            public String ownerModuleLabel() { return "模块A"; }
            public List<SecretPurposeDefinition> purposes() {
                return List.of(new SecretPurposeDefinition("QUERY", "查询"),
                    new SecretPurposeDefinition("QUERY", "重复查询"));
            }
        };

        assertThatThrownBy(() -> new SecretUsageCatalog(List.of(invalid)))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("重复凭据用途编码");
    }

    private static SecretUsageContributor contributor() {
        return new SecretUsageContributor() {
            @Override
            public String ownerModule() {
                return "govdata-datasource";
            }

            @Override
            public String ownerModuleLabel() {
                return "数据源管理";
            }

            @Override
            public List<SecretPurposeDefinition> purposes() {
                return List.of(
                    new SecretPurposeDefinition("DATASOURCE_CONNECTION_TEST", "数据源连接测试"),
                    new SecretPurposeDefinition("DATASOURCE_QUERY", "数据源授权查询"));
            }
        };
    }
}
