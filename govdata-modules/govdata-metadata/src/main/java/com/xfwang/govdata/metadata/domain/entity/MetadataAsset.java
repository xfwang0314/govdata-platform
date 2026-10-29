package com.xfwang.govdata.metadata.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 元数据稳定资产与当前技术状态。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gd_metadata_asset")
public class MetadataAsset extends TenantEntity {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId
    private Long assetId;
    private Long authzResourceId;
    private String assetCode;
    private String assetType;
    private Long parentAssetId;
    private Long datasourceId;
    private String connectorCode;
    private String connectorVersion;
    private String canonicalKey;
    private String nativeObjectId;
    private String originalName;
    private String displayName;
    private String businessName;
    private String description;
    private Long ownerDeptId;
    private Long stewardUserId;
    private String classificationCode;
    private Integer securityLevel;
    private String technicalStatus;
    private String governanceStatus;
    private LocalDateTime firstSeenTime;
    private LocalDateTime lastSeenTime;
    private Long lastHarvestRunId;
    private String structureHash;
    private String contentHash;
    private Integer currentRevisionNo;
    @Version
    private Integer lockVersion;
    @TableLogic
    private String delFlag;

}
