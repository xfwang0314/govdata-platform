package com.xfwang.govdata.api.datasource;

import java.util.Optional;

/** 数据源模块开放的非敏感稳定引用查询契约。 */
public interface DataSourceReferenceProvider {
    Optional<DataSourceReference> findById(Long datasourceId);
}
