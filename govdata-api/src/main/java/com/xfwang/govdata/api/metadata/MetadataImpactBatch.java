package com.xfwang.govdata.api.metadata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** 单个下游消费者的完整依赖集合；空集合表示撤销其全部投影。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataImpactBatch implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String tenantId;
    private String sourceModule;
    private String consumerType;
    private String consumerId;
    private List<MetadataImpactDependency> dependencies;
}
