package com.xfwang.govdata.api.metadata;

/** 下游领域向元数据影响索引投影依赖的稳定契约。 */
public interface MetadataImpactRegistry {

    /** 原子替换一个消费者的完整依赖集合，调用方拥有消费者事实。 */
    void replace(MetadataImpactBatch batch);
}
