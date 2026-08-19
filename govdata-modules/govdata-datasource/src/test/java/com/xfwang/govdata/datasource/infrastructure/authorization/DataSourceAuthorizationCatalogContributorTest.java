package com.xfwang.govdata.datasource.infrastructure.authorization;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class DataSourceAuthorizationCatalogContributorTest {

    @Test
    void exposesStableResourceSchemaForPolicyAndGrantEditors() {
        DataSourceAuthorizationCatalogContributor contributor =
            new DataSourceAuthorizationCatalogContributor();

        assertThat(contributor.moduleLabel()).isEqualTo("数据源管理");

        var definition = contributor.resourceTypes().get(0);

        assertThat(contributor.moduleCode()).isEqualTo("govdata-datasource");
        assertThat(definition.typeCode()).isEqualTo("govdata:datasource");
        assertThat(definition.actions()).extracting("code")
            .contains("VIEW", "TEST_CONNECTION", "METADATA_INSPECT", "AUTHORIZE");
        assertThat(definition.attributes()).extracting("path")
            .contains("resource.datasource.environment", "resource.datasource.connectorCode");
        assertThat(contributor.purposes()).extracting("code")
            .contains("DATASOURCE_ADMINISTRATION", "METADATA_INSPECTION");
        assertThat(contributor.purposes())
            .allSatisfy(purpose -> assertThat(purpose.resourceTypeCodes())
                .containsExactly("govdata:datasource"));
    }
}
