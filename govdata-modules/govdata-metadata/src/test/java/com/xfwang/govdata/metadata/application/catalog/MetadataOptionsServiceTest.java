package com.xfwang.govdata.metadata.application.catalog;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class MetadataOptionsServiceTest {

    @Test
    void exposesHierarchyAndGovernanceEnumsWithoutUnsupportedFileType() {
        var options = new MetadataOptionsService().options();

        assertThat(options.getAssetTypes()).extracting("value")
            .containsExactly("CATALOG", "SCHEMA", "TABLE", "COLUMN");
        assertThat(options.getGovernanceStatuses()).extracting("value")
            .containsExactly("UNMANAGED", "MANAGED", "ARCHIVED");
    }
}
