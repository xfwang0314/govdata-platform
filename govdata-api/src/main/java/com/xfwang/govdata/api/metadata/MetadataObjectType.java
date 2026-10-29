package com.xfwang.govdata.api.metadata;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 采集与目录发布共同使用的规范化元数据对象类型。
 *
 * <p>Catalog 和 Schema 是可选命名空间层级，Table 和 Column 是技术结构层级。</p>
 */
@Getter
@RequiredArgsConstructor
public enum MetadataObjectType {

    CATALOG("CATALOG", "Catalog", true),
    SCHEMA("SCHEMA", "Schema", true),
    TABLE("TABLE", "表", false),
    COLUMN("COLUMN", "字段", false),
    FILE("FILE", "文件", false);

    private final String code;
    private final String label;
    private final boolean namespace;
}
