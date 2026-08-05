package com.xfwang.govdata.datasource.domain.enums;

import com.xfwang.govdata.datasource.application.options.DataSourceOptionsQueryService;
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
        assertThat(DataSourceEnums.environment("PROD")).isEqualTo("PROD");
        assertThatThrownBy(() -> DataSourceEnums.status("DELETED")).isInstanceOf(RuntimeException.class);
    }
}
