package com.xfwang.govdata.metadata.harvest.application.plan;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class HarvestOptionsServiceTest {

    @Test
    void exposesRuleAndLifecycleEnums() {
        var options = new HarvestOptionsService().options();

        assertThat(options.getRuleScopes()).extracting("value")
            .containsExactly("CATALOG", "SCHEMA", "TABLE");
        assertThat(options.getRuleTypes()).extracting("value")
            .containsExactly("INCLUDE", "EXCLUDE");
        assertThat(options.getMatchTypes()).extracting("value")
            .contains("REGEX");
        assertThat(options.getPlanStatuses()).extracting("value")
            .containsExactly("ACTIVE", "DISABLED");
    }
}
