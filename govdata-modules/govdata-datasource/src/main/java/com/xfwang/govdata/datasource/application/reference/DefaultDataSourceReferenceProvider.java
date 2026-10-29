package com.xfwang.govdata.datasource.application.reference;

import com.xfwang.govdata.api.datasource.DataSourceReference;
import com.xfwang.govdata.api.datasource.DataSourceReferenceProvider;
import com.xfwang.govdata.datasource.domain.entity.DataSourceInstance;
import com.xfwang.govdata.datasource.infrastructure.persistence.mapper.DataSourceInstanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** 数据源非敏感稳定引用提供者。 */
@Service
@RequiredArgsConstructor
public class DefaultDataSourceReferenceProvider implements DataSourceReferenceProvider {
    private final DataSourceInstanceMapper mapper;

    @Override
    public Optional<DataSourceReference> findById(Long datasourceId) {
        DataSourceInstance value = datasourceId == null ? null : mapper.selectById(datasourceId);
        return Optional.ofNullable(value).map(source -> DataSourceReference.builder()
            .datasourceId(source.getDatasourceId())
            .resourceId(source.getAuthzResourceId())
            .ownerDeptId(source.getOwnerDeptId())
            .datasourceName(source.getDatasourceName())
            .connectorCode(source.getConnectorCode())
            .connectorVersion(source.getConnectorVersion())
            .status(source.getStatus())
            .build());
    }
}
