package com.xfwang.govdata.metadata.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 单张表不可变自动修订。
 */
@Data
@TableName("gd_metadata_table_revision")
public class MetadataTableRevision implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId
    private Long tableRevisionId;
    private String tenantId;
    private Long tableAssetId;
    private Integer revisionNo;
    private Integer previousRevisionNo;
    private Long harvestRunId;
    private String changeLevel;
    private String structureHash;
    private String contentHash;
    private String changeSummaryJson;
    private LocalDateTime observedTime;
    private String actorType;
    private String actorId;
    private String traceId;
    private LocalDateTime createTime;
}
