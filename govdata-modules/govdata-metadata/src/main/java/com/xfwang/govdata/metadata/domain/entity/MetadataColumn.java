package com.xfwang.govdata.metadata.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 字段资产当前技术明细。
 */
@Data
@TableName("gd_metadata_column")
public class MetadataColumn implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId
    private Long assetId;
    private String tenantId;
    private Long tableAssetId;
    private Integer revisionNo;
    private Integer ordinalPosition;
    private String dataType;
    private String nativeType;
    private Integer lengthValue;
    private Integer precisionValue;
    private Integer scaleValue;
    private Boolean nullableFlag;
    private Boolean primaryKeyFlag;
    private Boolean uniqueFlag;
    private Boolean autoIncrementFlag;
    private String defaultExpression;
    private String commentText;
    private String technicalStatus;
    private String structureHash;
    private String contentHash;
    private LocalDateTime firstSeenTime;
    private LocalDateTime lastSeenTime;
    private Long lastHarvestRunId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
