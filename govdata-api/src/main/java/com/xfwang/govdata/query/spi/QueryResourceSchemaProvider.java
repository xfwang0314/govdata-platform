package com.xfwang.govdata.query.spi;

import java.util.Optional;

/** 元数据模块实现的资源 Schema 查询 seam。 */
public interface QueryResourceSchemaProvider {

    Optional<QueryResourceSchema> findByResourceId(Long resourceId);
}
