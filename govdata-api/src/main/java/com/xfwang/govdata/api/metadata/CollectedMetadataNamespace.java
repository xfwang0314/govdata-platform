package com.xfwang.govdata.api.metadata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 一次采集中观察到的 Catalog 或 Schema 命名空间。
 *
 * <p>不同数据源可以缺省 Catalog 或 Schema，但只允许 Catalog 包含 Schema，
 * Schema 不能继续包含下级命名空间。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectedMetadataNamespace implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private MetadataObjectType namespaceType;
    private String namespaceName;
    private String canonicalKey;
    private String parentCanonicalKey;
    private String nativeObjectId;
    private String namespaceOwner;

}
