package com.xfwang.govdata.datasource.domain.enums;

import com.xfwang.govdata.datasource.application.options.DataSourceOptionsQueryService;
import com.xfwang.platform.api.auth.enums.SecurityLevel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class DataSourceEnumsTest {
    @Test
    void exposesTheSameClosedValuesUsedByValidation() {
        var options = new DataSourceOptionsQueryService().options();
        assertThat(options.getEnvironments()).extracting("value")
            .containsExactly("DEV", "TEST", "PROD");
        assertThat(options.getClassifications()).extracting("value")
            .containsExactly("PUBLIC", "INTERNAL", "SENSITIVE", "RESTRICTED");
        assertThat(options.getSecurityLevels()).hasSize(5);
        assertThat(SecurityLevel.supports(1)).isTrue();
        assertThat(SecurityLevel.supports(6)).isFalse();
        assertThat(options.getConnectionTestReasons()).extracting("value", "label")
            .contains(org.assertj.core.groups.Tuple.tuple("AUTHENTICATION_FAILED", "认证失败，请检查用户名和密码"),
                org.assertj.core.groups.Tuple.tuple("CONNECTED", "连接成功"));
        assertThat(DataSourceEnums.environment("PROD")).isEqualTo("PROD");
        assertThatThrownBy(() -> DataSourceEnums.status("DELETED")).isInstanceOf(RuntimeException.class);
    }
}
