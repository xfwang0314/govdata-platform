package com.xfwang.govdata.metadata.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 下游领域依赖元数据资产的可重建影响投影。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gd_metadata_impact_projection")
public class MetadataImpactProjection extends TenantEntity {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId
    private Long impactProjectionId;
    private String sourceModule;
    private String consumerType;
    private String consumerId;
    private String consumerCode;
    private String consumerName;
    private String consumerStatus;
    private Long assetId;
    private Long tableAssetId;
    private String dependencyType;
    private LocalDateTime sourceUpdatedTime;
}
