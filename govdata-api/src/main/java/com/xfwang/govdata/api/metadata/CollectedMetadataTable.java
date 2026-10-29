package com.xfwang.govdata.api.metadata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** 单张物理表的规范化采集结构。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectedMetadataTable implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String tableName;
    private String canonicalKey;
    private String parentCanonicalKey;
    private String nativeObjectId;
    private String tableType;
    private String comment;
    @Builder.Default
    private List<CollectedMetadataColumn> columns = new ArrayList<>();
}
