package com.xfwang.govdata.api.metadata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 下游模块声明的一条稳定元数据依赖。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataImpactDependency implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long assetId;
    private Long tableAssetId;
    private String dependencyType;
    private String consumerCode;
    private String consumerName;
    private String consumerStatus;
    private LocalDateTime sourceUpdatedTime;
}
