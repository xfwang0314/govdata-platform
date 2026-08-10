package com.xfwang.govdata.datasource.infrastructure.secret;

import com.xfwang.govdata.datasource.domain.enums.DataSourceSecretUsage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class DataSourceSecretUsageContributorTest {

    @Test
    void contributesTheDatasourceOwnedModuleAndPurposes() {
        var contributor = new DataSourceSecretUsageContributor();

        assertThat(contributor.ownerModule()).isEqualTo(DataSourceSecretUsage.OWNER_MODULE);
        assertThat(contributor.ownerModuleLabel()).isEqualTo(DataSourceSecretUsage.OWNER_MODULE_LABEL);
        assertThat(contributor.purposes()).extracting("code", "label")
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("DATASOURCE_CONNECTION_TEST", "数据源连接测试"),
                org.assertj.core.groups.Tuple.tuple("DATASOURCE_METADATA_INSPECT", "数据源元数据探测"),
                org.assertj.core.groups.Tuple.tuple("DATASOURCE_QUERY", "数据源授权查询"));
    }
}
