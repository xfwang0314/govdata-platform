package com.xfwang.govdata.datasource.infrastructure.secret;

import com.xfwang.govdata.datasource.domain.enums.DataSourceSecretUsage;
import com.xfwang.platform.api.secret.SecretPurposeDefinition;
import com.xfwang.platform.api.secret.SecretUsageContributor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/** 将数据源领域拥有的凭据用途贡献给平台凭据目录。 */
@Component
public class DataSourceSecretUsageContributor implements SecretUsageContributor {

    @Override
    public String ownerModule() {
        return DataSourceSecretUsage.OWNER_MODULE;
    }

    @Override
    public String ownerModuleLabel() {
        return DataSourceSecretUsage.OWNER_MODULE_LABEL;
    }

    @Override
    public List<SecretPurposeDefinition> purposes() {
        return Arrays.stream(DataSourceSecretUsage.values())
            .map(value -> new SecretPurposeDefinition(value.code(), value.label()))
            .toList();
    }
}
